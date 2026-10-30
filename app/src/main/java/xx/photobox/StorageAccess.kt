package xx.photobox

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageManager
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

/** The album opened on a first launch: the camera's own folder, when there is one. */
fun cameraAlbum(): File? =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera").takeIf { it.isDirectory }

/** Where a new album goes unless another folder is picked. */
fun defaultAlbumParent(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
