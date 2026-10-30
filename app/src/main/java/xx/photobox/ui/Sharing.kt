package xx.photobox.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import xx.photobox.isVideo
import java.io.File

/** [file] as a URI another app can be let read: it cannot read a plain path of ours. Through the app's FileProvider. */
internal fun contentUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/** [file]'s MIME type by its extension, or [fallback] when the phone does not know it. */
internal fun mimeTypeOf(file: File, fallback: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: fallback

/**
 * Offers [files] to whichever app the user picks to send them with: one by
 * ACTION_SEND, more by ACTION_SEND_MULTIPLE. Their RAW companions stay
 * behind — what is sent is what the grid shows.
 */
fun shareFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = files.map { contentUri(context, it) }
    val type = when {
        files.all { isVideo(it.path) } -> "video/*"
        files.none { isVideo(it.path) } -> "image/*"
        else -> "*/*"
    }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND)
            .setType(mimeTypeOf(files[0], type))
            .putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(type)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    // The read grant goes with the clip, not the extra: set here, the chooser hands it on to the app picked.
    send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: ActivityNotFoundException) {
        // The system chooser is always there; nothing to say if it is not.
    }
}
