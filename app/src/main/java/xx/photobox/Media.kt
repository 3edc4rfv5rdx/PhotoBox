package xx.photobox

import java.io.File

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "3gp", "webm", "mkv", "mov")
private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma", "ape", "alac", "aiff", "dsf")

/** Whether [path] names a video, by extension. */
fun isVideo(path: String): Boolean = File(path).extension.lowercase() in VIDEO_EXTENSIONS

/** RAW files a camera writes beside its JPEG: not shown, but trashed and restored with their photo. */
private val RAW_EXTENSIONS = setOf("dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "raf")

fun isRaw(file: File): Boolean = file.extension.lowercase() in RAW_EXTENSIONS

/**
 * The RAW files beside [file] under its name, which go wherever it goes, so a
 * pair is never split. A RAW has none of its own: it is the one that follows.
 */
fun companionsOf(file: File): List<File> {
    if (isRaw(file)) return emptyList()
    val dir = file.parentFile ?: return emptyList()
    val base = file.nameWithoutExtension
    // Listed, not guessed at as base.dng and base.DNG: shared storage ignores
    // case, so both guesses would find the one file.
    return dir.listFiles { f -> f.isFile && f.nameWithoutExtension == base && isRaw(f) }.orEmpty().sortedBy { it.name }
}

/** Whether [file] is music, by extension: a folder with any is a music album, its pictures covers. */
fun isAudio(file: File): Boolean = file.extension.lowercase() in AUDIO_EXTENSIONS

/** Whether [file] is a photo or video the gallery shows, by extension. */
fun isMedia(file: File): Boolean = file.extension.lowercase().let { it in IMAGE_EXTENSIONS || it in VIDEO_EXTENSIONS }


/** The file that tells galleries to pass a folder by, and what makes a folder hidden here. */
const val NOMEDIA_NAME = ".nomedia"
