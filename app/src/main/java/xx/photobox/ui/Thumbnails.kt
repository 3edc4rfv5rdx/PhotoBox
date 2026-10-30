package xx.photobox.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xx.photobox.isVideo
import java.io.File
import kotlin.math.abs

private const val THUMBNAIL_MAX_DIMENSION = 240
private val THUMBNAIL_PLAY_ICON = 28.dp

/** How far an embedded EXIF preview's proportions may stray from the photo's before
 * it is taken for a letterboxed one, whose black bars a cropped square would show. */
private const val THUMBNAIL_ASPECT_TOLERANCE = 0.02f

/** Thumbnail decodes share a few threads: a fast fling queues one per row it
 * passes, and one still waiting when its row scrolls away is cancelled before
 * it starts, instead of every row's decode running at once. */
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

/** Decoded thumbnails by path, bounded by bytes, so scrolling back or reopening
 * a list shows them at once. */
private val thumbnailCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** A small square preview of the photo or video at [path]. */
@Composable
fun PhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = thumbnailCache.get(path), key1 = path) {
        value = thumbnailCache.get(path)
            ?: withContext(thumbnailDispatcher) { withCancellationSignal { decodeThumbnail(path, it) } }?.also { thumbnailCache.put(path, it) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (isVideo(path)) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(THUMBNAIL_PLAY_ICON),
            )
        }
    }
}

/**
 * The camera's own preview from the file's EXIF block when it has the photo's
 * proportions — a few kilobytes read instead of the whole photo decoded —
 * otherwise a sampled decode of the photo itself.
 */
private fun decodeThumbnail(path: String, signal: CancellationSignal): Bitmap? {
    if (isVideo(path)) return videoFrame(path, THUMBNAIL_MAX_DIMENSION, signal)
    val exif = readExif(path)
    val embedded = exif?.takeIf { it.hasThumbnail() }?.thumbnailBitmap
    if (embedded != null) {
        val bounds = decodeBounds(path)
        // Both unrotated: the preview is stored the way the sensor wrote the photo.
        if (bounds.outWidth > 0 && bounds.outHeight > 0 && embedded.height > 0) {
            val photoAspect = bounds.outWidth.toFloat() / bounds.outHeight
            val previewAspect = embedded.width.toFloat() / embedded.height
            if (abs(photoAspect - previewAspect) <= photoAspect * THUMBNAIL_ASPECT_TOLERANCE) {
                return upright(embedded, exif)
            }
        }
    }
    return decodeSampled(path, THUMBNAIL_MAX_DIMENSION, signal)
}

private fun decodeBounds(path: String): BitmapFactory.Options =
    BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }

/** A still from the video at [path], fitted within [maxDimension], already
 * upright: the platform applies a video's rotation to the frames it hands out.
 * Anything it cannot read, or a [signal] cancelled mid-way, is a missing
 * still, not a crash. */
private fun videoFrame(path: String, maxDimension: Int, signal: CancellationSignal): Bitmap? = try {
    ThumbnailUtils.createVideoThumbnail(File(path), Size(maxDimension, maxDimension), signal)
} catch (e: Exception) {
    null
}

/**
 * Runs the blocking [block] with a [CancellationSignal] that fires the moment
 * this coroutine is cancelled — a page turned or a row scrolled away — so a
 * call that checks it gives up early instead of finishing for nobody.
 * [BitmapFactory] has no such hook; a video still does.
 */
private suspend fun <T> withCancellationSignal(block: (CancellationSignal) -> T): T = coroutineScope {
    val signal = CancellationSignal()
    // Unconfined and undispatched: the cancel runs on whichever thread cancels
    // this coroutine, not queued behind the very call it is meant to stop on a
    // dispatcher whose few threads that call may be holding.
    val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            signal.cancel()
        }
    }
    try {
        block(signal)
    } finally {
        watcher.cancel()
    }
}

/**
 * The file at [path] decoded at a power-of-two fraction of its size: no
 * smaller than [maxDimension] on a side, or, with [fit], no larger — a quarter
 * of the memory for a full-screen photo, and a quarter of the time.
 */
private fun decodeSampled(path: String, maxDimension: Int, signal: CancellationSignal, fit: Boolean = false): Bitmap? {
    if (isVideo(path)) return videoFrame(path, maxDimension, signal)
    val bounds = decodeBounds(path)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    if (fit) {
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    } else {
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    return applyExifRotation(bitmap, path)
}

/** [BitmapFactory] never applies a file's own EXIF orientation; a camera writes a
 * landscape sensor buffer plus this tag rather than rotating the pixels itself. */
private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap = upright(bitmap, readExif(path))

private fun readExif(path: String): ExifInterface? = try {
    ExifInterface(path)
} catch (e: Exception) {
    // A malformed EXIF block throws more than IOException; the photo is shown unturned.
    null
}

/** [bitmap] turned, and mirrored where the tag says so, the way [exif]'s orientation asks. */
private fun upright(bitmap: Bitmap, exif: ExifInterface?): Bitmap {
    val degrees = exif?.rotationDegrees ?: 0
    val flipped = exif?.isFlipped ?: false
    if (degrees == 0 && !flipped) return bitmap
    val matrix = Matrix().apply {
        // Mirror first, then turn: the library pairs a mirror with the turn that
        // follows it (transpose is 270, transverse 90), and the two do not commute.
        if (flipped) postScale(-1f, 1f)
        postRotate(degrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
