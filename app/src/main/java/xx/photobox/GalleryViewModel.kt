package xx.photobox

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Whether [old] and [new] hold the same files, with the same dates and sizes, in any order. */
internal fun sameItems(old: List<MediaItem>?, new: List<MediaItem>): Boolean {
    if (old == null || old.size != new.size) return false
    val byPath = old.associateBy { it.file.path }
    return new.all { item -> byPath[item.file.path]?.let { it.taken == item.taken && it.size == item.size } == true }
}

/** EXIF reads run a few at a time: enough to overlap the waits on storage, not so many they fight over it. */
private val dateReader = Dispatchers.IO.limitedParallelism(4)

private const val PROGRESS_TICK_MS = 100L

/** A new album being named: what is typed so far and the folder it goes into. */
data class AlbumDraft(val name: String, val parent: File)

/** The open album and the list of albums, kept across a recreated activity. */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    var hasStorageAccess by mutableStateOf(hasAllFilesAccess())
        private set

    /** The album the thumbnails show; null when none is open. */
    var album by mutableStateOf<File?>(null)
        private set

    /** Every album found by the last scan, hidden ones included; null before the first scan ends. */
    var albums by mutableStateOf<List<Album>?>(null)
        private set

    var scanning by mutableStateOf(false)
        private set

    /** The folders the last scan went through — the search roots, or the volumes when there are none — for the list's sections. */
    var scannedRoots by mutableStateOf<List<File>>(emptyList())
        private set

    /** The open album's files, unsorted; null while they are being read. */
    var items by mutableStateOf<List<MediaItem>?>(null)
        private set

    /** Dates read so far out of [datesTotal] while an album's new files are read; 0 of 0 when every date was cached. */
    var datesDone by mutableIntStateOf(0)
        private set
    var datesTotal by mutableIntStateOf(0)
        private set

    private val dateCache = DateCache(File(application.filesDir, "dates.tsv"))
    private var itemsJob: Job? = null
    /** The album [items] belong to, so a reload of the same one keeps them on screen while it runs. */
    private var itemsAlbum: File? = null

    /** Everything in the trashes, newest first; null while it is being looked through. */
    var trashEntries by mutableStateOf<List<TrashEntry>?>(null)
        private set

    /** Set while the trash is being changed — a restore, a delete, emptying it — so nothing else starts meanwhile. */
    var trashBusy by mutableStateOf(false)
        private set

    /** Restoring or emptying the whole trash while it runs, with how far it has got; null otherwise. */
    var trashJob by mutableStateOf<TrashJob?>(null)
        private set
    var trashJobDone by mutableIntStateOf(0)
        private set
    var trashJobTotal by mutableIntStateOf(0)
        private set

    /** What the last step on files has to say, for a toast; cleared once said. */
    var notice by mutableStateOf<FileNotice?>(null)
        private set

    /** Moving, copying or trashing the album's files while it runs, with how far it has got; null otherwise. */
    var fileJob by mutableStateOf<FileJob?>(null)
        private set
    var fileJobDone by mutableIntStateOf(0)
        private set
    var fileJobTotal by mutableIntStateOf(0)
        private set

    /** Whether files are being changed — the trash, or the album's files moved, copied or trashed — so nothing else starts meanwhile. */
    val busy: Boolean
        get() = trashBusy || fileJob != null

    /** The new-album dialog's contents while it is open, or while its folder is being picked. */
    var draft by mutableStateOf<AlbumDraft?>(null)
        private set

    var createFailed by mutableStateOf(false)
        private set

    private var scanJob: Job? = null

    /** Whether the activity has resumed before: the first resume follows the first load, and need not repeat it. */
    private var resumed = false

    /** Set while a folder is being made, so a second tap on Create makes no second attempt. */
    private var creating = false

    init {
        if (hasStorageAccess) {
            album = startAlbum()
            purgeOldTrash()
        }
    }

    /** Called on every resume: the grant is flipped in system Settings, outside the app. */
    fun refreshStorageAccess() {
        val granted = hasAllFilesAccess()
        if (granted && !hasStorageAccess) {
            if (album == null) album = startAlbum()
            purgeOldTrash()
        }
        hasStorageAccess = granted
        // Files may have come and gone in another app meanwhile.
        if (resumed && granted && itemsAlbum != null) loadItems()
        resumed = true
    }

    /** The album open last, while it is still there; on a first launch the camera's folder. */
    private fun startAlbum(): File? {
        val saved = AppSettings.albumPath(getApplication())
        return if (saved != null) File(saved).takeIf { it.isDirectory } else cameraAlbum()
    }

    fun openAlbum(dir: File) {
        album = dir
        AppSettings.setAlbumPath(getApplication(), dir.path)
    }

    /**
     * Reads the open album's files and when each was taken. Dates come from
     * [dateCache] where it has them; the rest are read a few at a time, with
     * progress, and written back. A reload of the album already shown keeps
     * its files on screen until the new list is ready.
     */
    fun loadItems() {
        val dir = album ?: return
        if (!hasStorageAccess) return
        itemsJob?.cancel()
        if (dir != itemsAlbum) items = null
        itemsAlbum = dir
        itemsJob = viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                dir.listFiles().orEmpty().filter { it.isFile && isMedia(it) }
            }
            val known = ArrayList<MediaItem>(files.size)
            val unknown = ArrayList<Triple<File, Long, Long>>()
            withContext(Dispatchers.IO) {
                for (file in files) {
                    val modified = file.lastModified()
                    val size = file.length()
                    val taken = dateCache.get(file, modified, size)
                    if (taken != null) known += MediaItem(file, taken, size) else unknown += Triple(file, modified, size)
                }
            }
            datesDone = 0
            datesTotal = unknown.size
            val done = AtomicInteger(0)
            // Counted on the reading threads, shown from this one: Compose
            // state is not written from a pool of threads at once.
            val ticker = launch {
                while (true) {
                    datesDone = done.get()
                    delay(PROGRESS_TICK_MS)
                }
            }
            val read = withContext(dateReader) {
                unknown.map { (file, modified, size) ->
                    async {
                        val taken = takenAt(file)
                        dateCache.put(file, modified, size, taken)
                        done.incrementAndGet()
                        MediaItem(file, taken, size)
                    }
                }.awaitAll()
            }
            ticker.cancel()
            val loaded = known + read
            // The same files as before keep the same list, so nothing built
            // on it — the sorted grid, the open viewer — starts over for nothing.
            if (!sameItems(items, loaded)) items = loaded
            datesTotal = 0
            if (read.isNotEmpty()) withContext(Dispatchers.IO) { dateCache.save() }
        }
    }

    /** The folders albums and trashes are looked for in: the search roots, or every volume without any. */
    private fun searchRoots(): List<File> =
        AppSettings.roots.value.map(::File).ifEmpty { storageRoots(getApplication()).map { it.dir } }

    /**
     * Looks for albums again, in the search roots or, without any, on every
     * volume. The last list stays on screen meanwhile, so coming back to the
     * album list shows it at once and only then updates. A scan under way is
     * left to finish, unless [restart] — the roots changed under it.
     */
    fun scanAlbums(restart: Boolean = false) {
        if (!hasStorageAccess) return
        if (scanJob?.isActive == true) {
            if (!restart) return
            scanJob?.cancel()
        }
        scanning = true
        scanJob = viewModelScope.launch {
            try {
                val roots = searchRoots()
                albums = withContext(Dispatchers.IO) { AlbumScanner.scan(roots) { ensureActive() } }
                scannedRoots = roots
            } finally {
                // A cancelled scan leaves the flag to the one that replaced it.
                if (scanJob == coroutineContext[Job]) scanning = false
            }
        }
    }

    /** Opens the new-album dialog on [parent], or on the first search root, so the new album is found where it is made. */
    fun startDraft(parent: File? = null) {
        createFailed = false
        draft = AlbumDraft(
            "",
            parent ?: AppSettings.roots.value.firstOrNull()?.let(::File)?.takeIf { it.isDirectory } ?: defaultAlbumParent(),
        )
    }

    fun editDraft(name: String) {
        createFailed = false
        draft = draft?.copy(name = name)
    }

    fun setDraftParent(parent: File) {
        draft = draft?.copy(parent = parent)
    }

    fun cancelDraft() {
        draft = null
    }

    /**
     * Makes the folder and opens it. It is not added to [albums] here: the
     * next scan finds it, an empty album.
     */
    fun createAlbum(onCreated: () -> Unit) {
        val current = draft ?: return
        if (creating || checkAlbumName(current.parent, current.name) != AlbumNameCheck.OK) return
        val dir = File(current.parent, current.name.trim())
        creating = true
        viewModelScope.launch {
            val made = withContext(Dispatchers.IO) { dir.mkdirs() }
            if (made) {
                draft = null
                openAlbum(dir)
                onCreated()
            } else {
                createFailed = true
            }
            creating = false
        }
    }

    /**
     * Deletes the empty album [dir] and reads the albums again; when it was the
     * open one, none is open after. A folder something has landed in since the
     * scan is left alone.
     */
    fun deleteEmptyAlbum(dir: File) {
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) { dir.listFiles()?.isEmpty() == true && dir.delete() }
            if (!gone) {
                notice = FileNotice.DELETE_FAILED
            } else if (album == dir) {
                album = null
            }
            scanAlbums(restart = true)
        }
    }

    // ---------- Trash ----------

    /** The trash of every volume, whether it exists yet or not. */
    private fun trashDirs(): List<File> = storageRoots(getApplication()).map { Trash.dirOn(it.dir) }

    /**
     * Once per start: carries the old per-folder trashes over, the first time
     * only, then deletes for good what has been in the trash longer than the
     * setting allows.
     */
    private fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - AppSettings.trashDays.value * DAY_MS
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (!AppSettings.trashMigrated(getApplication())) {
                    Trash.migrateLegacy(searchRoots(), { volumeRootOf(getApplication(), it)?.let(Trash::dirOn) }) {
                        ensureActive()
                    }
                    AppSettings.setTrashMigrated(getApplication())
                }
                Trash.purgeOlderThan(trashDirs(), cutoff)
            }
        }
    }

    fun loadTrash() {
        if (!hasStorageAccess) return
        viewModelScope.launch {
            trashEntries = withContext(Dispatchers.IO) { Trash.list(trashDirs()) }
        }
    }

    /**
     * Moves [files], each with its RAW companions, into the trash of its own
     * volume, with progress in [fileJobDone] of [fileJobTotal]. [onDone] hears
     * whether all of them went, on the main thread; the open album is read
     * again either way.
     */
    fun moveToTrash(files: List<File>, onDone: (Boolean) -> Unit = {}) {
        if (busy || files.isEmpty()) return
        trashBusy = true
        viewModelScope.launch {
            val failed = runFileJob(FileJob.TRASH, files) { file ->
                val group = listOf(file) + companionsOf(file)
                val trashDir = volumeRootOf(getApplication(), file)?.let(Trash::dirOn)
                if (trashDir != null && Trash.moveToTrash(file, trashDir, System.currentTimeMillis())) {
                    group.map { it.path }
                } else {
                    null
                }
            }
            trashBusy = false
            if (failed > 0) notice = FileNotice.DELETE_FAILED
            onDone(failed == 0)
            loadItems()
        }
    }

    /**
     * Moves or copies [files], each with its RAW companions, into [dir], with
     * progress in [fileJobDone] of [fileJobTotal]. A file moved into the
     * folder it is in stays as it is. [onDone] hears whether all of them went;
     * the open album is read again either way.
     */
    fun transfer(files: List<File>, dir: File, mode: Transfer, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        val groups = files.filter { mode == Transfer.COPY || it.parentFile?.path != dir.path }
        if (groups.isEmpty()) {
            onDone(true)
            return
        }
        viewModelScope.launch {
            val job = if (mode == Transfer.MOVE) FileJob.MOVE else FileJob.COPY
            val failed = runFileJob(job, groups) { file ->
                val group = listOf(file) + companionsOf(file)
                transferGroup(group, dir, mode)?.let { targets ->
                    targets.map { it.path } + if (mode == Transfer.MOVE) group.map { it.path } else emptyList()
                }
            }
            if (failed > 0) notice = if (mode == Transfer.MOVE) FileNotice.MOVE_FAILED else FileNotice.COPY_FAILED
            onDone(failed == 0)
            loadItems()
        }
    }

    /**
     * Runs [step] on each of [files] off the main thread as [job], counting
     * them into [fileJobDone]. A step returns the paths it changed, for the
     * system's media index, or null when it failed. How many failed.
     */
    private suspend fun runFileJob(job: FileJob, files: List<File>, step: (File) -> List<String>?): Int {
        fileJob = job
        fileJobTotal = files.size
        fileJobDone = 0
        val done = AtomicInteger(0)
        return coroutineScope {
            // Counted on the worker, shown from here: Compose state is not written from other threads.
            val ticker = launch {
                while (true) {
                    fileJobDone = done.get()
                    delay(PROGRESS_TICK_MS)
                }
            }
            val failed = withContext(Dispatchers.IO) {
                val changed = mutableListOf<String>()
                var failed = 0
                for (file in files) {
                    val paths = step(file)
                    if (paths == null) failed++ else changed += paths
                    done.incrementAndGet()
                }
                announceChanged(getApplication(), changed)
                failed
            }
            ticker.cancel()
            fileJob = null
            failed
        }
    }

    fun restore(entry: TrashEntry) = changeTrash {
        when (Trash.restore(entry)) {
            RestoreResult.OK -> {
                announceChanged(getApplication(), listOf(entry.original.path))
                null
            }
            RestoreResult.EXISTS -> FileNotice.RESTORE_EXISTS
            RestoreResult.FAILED -> FileNotice.RESTORE_FAILED
        }
    }

    fun purge(entry: TrashEntry) = changeTrash {
        if (Trash.purge(entry)) null else FileNotice.DELETE_FAILED
    }

    /** Puts back everything it can; what would land on a file already there stays in the trash. */
    fun restoreAll() = changeAllOfTrash(TrashJob.RESTORE) { entry ->
        (Trash.restore(entry) == RestoreResult.OK).also { ok ->
            if (ok) announceChanged(getApplication(), listOf(entry.original.path))
        }
    }

    fun emptyTrash() = changeAllOfTrash(TrashJob.EMPTY, Trash::purge)

    /**
     * [step] on every entry, counted for the screen as it goes, then the
     * trash and the open album read again. An entry [step] fails on stays
     * in the trash and is reported once all are through.
     */
    private fun changeAllOfTrash(job: TrashJob, step: (TrashEntry) -> Boolean) {
        val entries = trashEntries.orEmpty()
        // Checked here, not only in changeTrash: the job is set before it starts.
        if (trashBusy || entries.isEmpty()) return
        trashJob = job
        trashJobTotal = entries.size
        trashJobDone = 0
        val done = AtomicInteger(0)
        changeTrash {
            val failed = entries.count { entry -> !step(entry).also { done.incrementAndGet() } }
            when {
                failed == 0 -> null
                job == TrashJob.RESTORE -> FileNotice.RESTORE_SOME_LEFT
                else -> FileNotice.DELETE_FAILED
            }
        }
        // Counted on the worker, shown from here: Compose state is not written from other threads.
        viewModelScope.launch {
            while (trashJob == job) {
                trashJobDone = done.get()
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    fun noticeShown() {
        notice = null
    }

    /** Runs [step] off the main thread with the trash marked busy, then reads the trash and the open album again. */
    private fun changeTrash(step: () -> FileNotice?) {
        if (trashBusy) return
        trashBusy = true
        viewModelScope.launch {
            val said = withContext(Dispatchers.IO) { step() }
            trashEntries = withContext(Dispatchers.IO) { Trash.list(trashDirs()) }
            trashBusy = false
            trashJob = null
            said?.let { notice = it }
            loadItems()
        }
    }
}

/** A step on the whole trash, shown with its progress while it runs. */
enum class TrashJob { RESTORE, EMPTY }

/** A step on the album's files, shown with its progress while it runs. */
enum class FileJob { MOVE, COPY, TRASH }

/** What a step on files reports when it did not all go as asked. */
enum class FileNotice { DELETE_FAILED, RESTORE_EXISTS, RESTORE_FAILED, RESTORE_SOME_LEFT, MOVE_FAILED, COPY_FAILED }

private const val DAY_MS = 24L * 60 * 60 * 1000
