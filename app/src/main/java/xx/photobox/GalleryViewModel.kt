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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
        if (hasStorageAccess) album = startAlbum()
    }

    /** Called on every resume: the grant is flipped in system Settings, outside the app. */
    fun refreshStorageAccess() {
        val granted = hasAllFilesAccess()
        if (granted && !hasStorageAccess && album == null) album = startAlbum()
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
            items = known + read
            datesTotal = 0
            if (read.isNotEmpty()) withContext(Dispatchers.IO) { dateCache.save() }
        }
    }

    /**
     * Looks for albums again. The last list stays on screen meanwhile, so
     * coming back to the album list shows it at once and only then updates.
     */
    fun scanAlbums() {
        if (scanJob?.isActive == true || !hasStorageAccess) return
        scanning = true
        scanJob = viewModelScope.launch {
            try {
                val roots = storageRoots(getApplication()).map { it.dir }
                albums = withContext(Dispatchers.IO) { AlbumScanner.scan(roots) { ensureActive() } }
            } finally {
                scanning = false
            }
        }
    }

    fun startDraft() {
        createFailed = false
        draft = AlbumDraft("", defaultAlbumParent())
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
     * Makes the folder and opens it. It is not added to [albums]: an empty
     * folder is no album until something is put in it, and the next scan says so.
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
}
