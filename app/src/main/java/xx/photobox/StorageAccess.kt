package xx.photobox

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.net.toUri
import java.io.File

/** Whether this app currently holds "All files access" — every file
 * operation in the app relies on it being granted. */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

/** The system settings screen that grants or revokes it. There is no normal
 * runtime permission dialog for this one — it is sensitive enough that the
 * system requires the user to flip it on by hand. */
fun allFilesAccessIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())

/** A volume the folder picker can start from, named the way the system names it. */
class StorageRoot(val dir: File, val label: String)

/**
 * Every storage volume this app can walk — internal storage, and an SD card
 * when one is in. Walked with plain java.io.File under [hasAllFilesAccess]
 * rather than through the system's document picker, which asks the user to
 * confirm every folder it hands over.
 */
fun storageRoots(context: Context): List<StorageRoot> =
    context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
        volume.directory?.takeIf { it.isDirectory }?.let { StorageRoot(it, volume.getDescription(context)) }
    }

/** [dir] as a path inside [volume] — "DCIM/Camera", not
 * "/storage/emulated/0/DCIM/Camera". The full path when [dir] is the volume
 * itself or lies outside it. */
fun pathOnVolume(dir: File, volume: File): String {
    val prefix = volume.path.trimEnd('/') + "/"
    return if (dir.path.startsWith(prefix)) dir.path.removePrefix(prefix) else dir.path
}

/** The root of the volume [file] is on — where its trash is, so moving a photo there is a rename, never a copy. */
fun volumeRootOf(context: Context, file: File): File? =
    context.getSystemService(StorageManager::class.java).getStorageVolume(file)?.directory

/** The album opened on a first launch: the camera's own folder, when there is one. */
fun cameraAlbum(): File? =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera").takeIf { it.isDirectory }

/** The search roots before any are chosen: where the camera and most apps put pictures. */
fun defaultRoots(): List<String> = listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES)
    .map { Environment.getExternalStoragePublicDirectory(it).path }

/** Where a new album goes unless another folder is picked, when there is no search root to put it in. */
fun defaultAlbumParent(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

/**
 * Tells the system's media index that [paths] changed — a file trashed,
 * restored, moved or gone. Other galleries read that index, not the files, and
 * without this keep showing a photo where it was until the system scans by itself.
 */
fun announceChanged(context: Context, paths: List<String>) {
    if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
}

private const val EXTERNAL_STORAGE_DOCUMENTS = "com.android.externalstorage.documents"

/**
 * The file behind [uri] another app handed over, when it can be found and
 * read: a file URI, the system's own storage documents ("primary:DCIM/x.jpg",
 * "1A2B-3C4D:DCIM/x.jpg"), the media library's path for its entries, or a
 * path inside a URI that carries one whole, as many file managers' do. Null
 * otherwise — the file is then read through [uri] itself.
 */
fun fileFromUri(context: Context, uri: Uri): File? {
    val candidates = sequence {
        when (uri.scheme) {
            "file" -> uri.path?.let { yield(File(it)) }
            "content" -> {
                if (uri.authority == EXTERNAL_STORAGE_DOCUMENTS && DocumentsContract.isDocumentUri(context, uri)) {
                    val (volume, path) = DocumentsContract.getDocumentId(uri).split(':', limit = 2).let {
                        it[0] to it.getOrElse(1) { "" }
                    }
                    val root = if (volume == "primary") {
                        Environment.getExternalStorageDirectory()
                    } else {
                        storageRoots(context).firstOrNull { it.dir.name == volume }?.dir
                    }
                    root?.let { yield(File(it, path)) }
                }
                mediaDataPath(context, uri)?.let { yield(File(it)) }
                // A provider that puts the whole path in its URI.
                uri.path?.let { path ->
                    val at = path.indexOf("/storage/")
                    if (at >= 0) yield(File(path.substring(at)))
                }
            }
        }
    }
    return candidates.firstOrNull { it.isFile && it.canRead() }
}

/** The path the media library or a provider like it gives for [uri], if any. Deprecated as a column, still filled in. */
@Suppress("DEPRECATION")
private fun mediaDataPath(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: Exception) {
    // A provider that has no such column, or refuses the query.
    null
}

/** The name [uri] goes by, for a copy of a file no path was found for. */
fun displayNameOf(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: Exception) {
    null
}
