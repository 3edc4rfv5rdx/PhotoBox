package xx.photobox

import java.io.File

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "3gp", "webm", "mkv", "mov")
private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma", "ape", "alac", "aiff", "dsf")

/** Whether [path] names a video, by extension. */
fun isVideo(path: String): Boolean = File(path).extension.lowercase() in VIDEO_EXTENSIONS

/** Whether [file] is music, by extension: a folder with any is a music album, its pictures covers. */
fun isAudio(file: File): Boolean = file.extension.lowercase() in AUDIO_EXTENSIONS

/** Whether [file] is a photo or video the gallery shows, by extension. */
fun isMedia(file: File): Boolean = file.extension.lowercase().let { it in IMAGE_EXTENSIONS || it in VIDEO_EXTENSIONS }

/** The trash folder inside every album something was deleted from. Never an album itself. */
const val TRASH_DIR_NAME = ".PhotoTrash"

/** The file that tells galleries to pass a folder by, and what makes a folder hidden here. */
const val NOMEDIA_NAME = ".nomedia"
