package xx.photobox

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    /** The new-album dialog's contents while it is open, or while its folder is being picked. */
    var draft by mutableStateOf<AlbumDraft?>(null)
        private set

    var createFailed by mutableStateOf(false)
        private set

    private var scanJob: Job? = null

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
