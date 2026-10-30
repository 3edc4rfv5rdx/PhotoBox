package xx.photobox

import android.content.Intent
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Another app asking for photos or videos to be picked and handed back —
 * GET_CONTENT or PICK. [types] are the MIME types it takes, wildcards
 * included; [multiple] when it takes more than one.
 */
class PickRequest(val types: List<String>, val multiple: Boolean) {

    /** Whether [file] is of a type asked for. */
    fun accepts(file: File): Boolean {
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: if (isVideo(file.path)) "video/*" else "image/*"
        return types.any { mimeMatches(it, type) }
    }

    companion object {
        /** The request [intent] makes, or null when it is no request to pick. */
        fun from(intent: Intent): PickRequest? {
            if (intent.action != Intent.ACTION_GET_CONTENT && intent.action != Intent.ACTION_PICK) return null
            val types = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()?.takeIf { it.isNotEmpty() }
                ?: listOf(intent.type ?: "*/*")
            return PickRequest(types.map(::pickedType), intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        }
    }
}

/** [type] as asked for, with the media library's own "vnd.android.cursor.dir/image" read as any image, and its video one as any video. */
internal fun pickedType(type: String): String = when (type) {
    "vnd.android.cursor.dir/image" -> "image/*"
    "vnd.android.cursor.dir/video" -> "video/*"
    else -> type.lowercase()
}

/** Whether [type] falls under [pattern]: the very type, its family under a star, or anything under two. */
internal fun mimeMatches(pattern: String, type: String): Boolean {
    val (family, sub) = pattern.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "*" } }
    val typeFamily = type.substringBefore('/')
    return when {
        family == "*" -> true
        family != typeFamily -> false
        sub == "*" -> true
        else -> pattern == type
    }
}
