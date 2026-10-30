package xx.photobox

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

/** Whether this app currently holds "All files access" — every file
 * operation in the app relies on it being granted. */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

/** The system settings screen that grants or revokes it. There is no normal
 * runtime permission dialog for this one — it is sensitive enough that the
 * system requires the user to flip it on by hand. */
fun allFilesAccessIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
