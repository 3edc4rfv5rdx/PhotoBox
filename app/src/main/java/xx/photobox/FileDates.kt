package xx.photobox

import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * When the photo or video in [file] was taken: the camera's own record of it —
 * EXIF for a photo, the container's date for a video — or the file's last
 * change when it has none. Reads the file, so off the main thread.
 */
fun takenAt(file: File): Long = recordedAt(file) ?: file.lastModified()

/** The camera's own record of when [file] was taken, as [takenAt] reads it; null when it has none. */
fun recordedAt(file: File): Long? =
    if (isVideo(file.path)) parseVideoDate(videoDateText(file)) else parseExifDate(exifDateText(file))

private fun exifDateText(file: File): String? = try {
    val exif = ExifInterface(file.path)
    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
} catch (e: Exception) {
    // A malformed EXIF block throws more than IOException; it is a missing date, not a crash.
    null
}

private fun videoDateText(file: File): String? = try {
    MediaMetadataRetriever().use {
        it.setDataSource(file.path)
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
    }
} catch (e: Exception) {
    // Anything the platform cannot read is a missing date, not a crash.
    null
}

/**
 * EXIF's "yyyy:MM:dd HH:mm:ss". It carries no time zone: it is the camera's
 * wall clock, so it is read in the local zone and shown back unchanged. A
 * camera with no clock set writes zeros or blanks, which are no date at all.
 */
internal fun parseExifDate(text: String?): Long? = parseDate(text, "yyyy:MM:dd HH:mm:ss", utc = false)

/**
 * A video container's date, UTC: "yyyyMMdd'T'HHmmss.SSS'Z'", some writers
 * leaving the milliseconds out. An unset one comes back as 1904-01-01, the
 * container format's own zero, which lands before 1970.
 */
internal fun parseVideoDate(text: String?): Long? =
    (parseDate(text, "yyyyMMdd'T'HHmmss.SSS'Z'", utc = true) ?: parseDate(text, "yyyyMMdd'T'HHmmss'Z'", utc = true))
        ?.takeIf { it > 0 }

private fun parseDate(text: String?, pattern: String, utc: Boolean): Long? {
    if (text.isNullOrBlank()) return null
    val format = SimpleDateFormat(pattern, Locale.US).apply {
        // Strict: a lenient parse turns "0000:00:00 00:00:00" into a date in year 2 BC.
        isLenient = false
        if (utc) timeZone = TimeZone.getTimeZone("UTC")
    }
    return try {
        format.parse(text.trim())?.time
    } catch (e: ParseException) {
        null
    }
}
