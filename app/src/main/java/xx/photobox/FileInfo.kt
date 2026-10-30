package xx.photobox

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

/**
 * What the info dialog shows about one file. Anything the file does not say
 * is null. [taken] is the camera's own record only, unlike [takenAt], which
 * falls back to the file's time.
 */
class FileInfo(
    val file: File,
    val size: Long,
    val modified: Long,
    val width: Int?,
    val height: Int?,
    val taken: Long?,
    val durationMs: Long?,
    val camera: String?,
    val fNumber: Double?,
    val exposureSeconds: Double?,
    val iso: Int?,
    val focalLengthMm: Double?,
    val latitude: Double?,
    val longitude: Double?,
)

/** Reads [file]'s [FileInfo]: EXIF for a photo, the container for a video. Reads the file, so off the main thread. */
fun readFileInfo(file: File): FileInfo =
    if (isVideo(file.path)) readVideoInfo(file) else readPhotoInfo(file)

private fun readPhotoInfo(file: File): FileInfo {
    val exif = try {
        ExifInterface(file.path)
    } catch (e: Exception) {
        // A malformed EXIF block throws more than IOException; the rest still shows.
        null
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(file.path, it) }
    // As the photo is seen, not as the sensor wrote it.
    val turned = exif?.rotationDegrees == 90 || exif?.rotationDegrees == 270
    val width = bounds.outWidth.takeIf { it > 0 }
    val height = bounds.outHeight.takeIf { it > 0 }
    val latLong = exif?.latLong
    return FileInfo(
        file = file,
        size = file.length(),
        modified = file.lastModified(),
        width = if (turned) height else width,
        height = if (turned) width else height,
        taken = recordedAt(file),
        durationMs = null,
        camera = exif?.let { cameraName(it.getAttribute(ExifInterface.TAG_MAKE), it.getAttribute(ExifInterface.TAG_MODEL)) },
        fNumber = exif?.positiveDouble(ExifInterface.TAG_F_NUMBER),
        exposureSeconds = exif?.positiveDouble(ExifInterface.TAG_EXPOSURE_TIME),
        iso = exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)?.takeIf { it > 0 },
        focalLengthMm = exif?.positiveDouble(ExifInterface.TAG_FOCAL_LENGTH),
        latitude = latLong?.get(0),
        longitude = latLong?.get(1),
    )
}

private fun ExifInterface.positiveDouble(tag: String): Double? = getAttributeDouble(tag, 0.0).takeIf { it > 0.0 }

private fun readVideoInfo(file: File): FileInfo {
    var width: Int? = null
    var height: Int? = null
    var duration: Long? = null
    var location: Pair<Double, Double>? = null
    try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path)
            fun number(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
            val w = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
            val h = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
            val rotation = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            val turned = rotation == 90 || rotation == 270
            width = if (turned) h else w
            height = if (turned) w else h
            duration = number(MediaMetadataRetriever.METADATA_KEY_DURATION)
            location = parseIso6709(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
        }
    } catch (e: Exception) {
        // Anything the platform cannot read is missing info, not a crash.
    }
    return FileInfo(
        file = file,
        size = file.length(),
        modified = file.lastModified(),
        width = width?.takeIf { it > 0 },
        height = height?.takeIf { it > 0 },
        taken = recordedAt(file),
        durationMs = duration?.takeIf { it > 0 },
        camera = null,
        fNumber = null,
        exposureSeconds = null,
        iso = null,
        focalLengthMm = null,
        latitude = location?.first,
        longitude = location?.second,
    )
}

/** "Canon Canon EOS R6" reads as "Canon EOS R6": many makers repeat their name in the model. */
internal fun cameraName(make: String?, model: String?): String? {
    val m = make?.trim().orEmpty()
    val mo = model?.trim().orEmpty()
    return when {
        mo.isEmpty() -> m.ifEmpty { null }
        m.isEmpty() || mo.startsWith(m, ignoreCase = true) -> mo
        else -> "$m $mo"
    }
}

/** A shutter time as photographers write it: 1/125 under a second, 2 or 1.5 from one up. No unit. */
internal fun exposureText(seconds: Double): String =
    if (seconds < 1.0) "1/${(1.0 / seconds).roundToLong()}" else trimmedNumber(seconds)

/** [value] with at most one decimal, and none when it is whole: 1.8, 4, 26.5. */
internal fun trimmedNumber(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

/**
 * A video's place as its container writes it, ISO 6709: "+37.4219-122.0840/",
 * an altitude sometimes following. Null when there is none or it does not parse.
 */
internal fun parseIso6709(text: String?): Pair<Double, Double>? {
    val match = Regex("""^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(text?.trim() ?: return null) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
}
