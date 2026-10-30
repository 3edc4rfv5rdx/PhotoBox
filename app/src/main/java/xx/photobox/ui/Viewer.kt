package xx.photobox.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import xx.photobox.MediaItem
import xx.photobox.R
import xx.photobox.isVideo
import java.io.File

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val PHOTO_MAX_DIMENSION = 2048

private val PLAY_BUTTON = 80.dp
private val PLAY_ICON = 56.dp

/**
 * The album's files full screen, [sorted] as the grid shows them, opened on
 * [startPath]. A swipe goes to the next or previous one; a pinch or a double
 * tap zooms, and while zoomed a drag moves the photo instead of the page. A
 * tap shows or hides the bar on top. [onPage] hears each file settled on, so
 * the caller can open on it again after the list is reloaded.
 */
@Composable
fun ViewerScreen(
    sorted: List<MediaItem>,
    startPath: String,
    onPage: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val start = sorted.indexOfFirst { it.file.path == startPath }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start) { sorted.size }
    // One zoom, for the page in front; every other page shows unzoomed.
    val zoom = remember { PhotoZoom() }
    // Derived, so the pager hears zoomed or not, not every step of a pinch.
    val zoomed by remember { derivedStateOf { zoom.scale > 1f } }
    var chrome by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage) { zoom.reset() }
    LaunchedEffect(pagerState.settledPage) {
        sorted.getOrNull(pagerState.settledPage)?.let { onPage(it.file.path) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
            key = { sorted[it].file.path },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val path = sorted[page].file.path
            PhotoPage(
                path = path,
                zoom = if (page == pagerState.currentPage) zoom else null,
                next = sorted.getOrNull(page + 1)?.file?.path,
                onTap = { chrome = !chrome },
            )
        }

        if (chrome) {
            val current = sorted.getOrNull(pagerState.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LargeIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClose, tint = Color.White)
                Text(
                    current?.file?.name.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    "${pagerState.currentPage + 1} / ${sorted.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

/** How far the photo in front is zoomed in and where to. */
@Stable
class PhotoZoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /**
     * One step of a pinch or drag: [zoomChange] about [centroid], so the spot
     * between the fingers stays under them, then [pan]. The layer scales about
     * the middle of [size], which is where the centroid is measured from.
     */
    fun transform(zoomChange: Float, pan: Offset, centroid: Offset, size: IntSize) {
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        val applied = newScale / scale
        val fromCenter = if (centroid.isSpecified) centroid - Offset(size.width / 2f, size.height / 2f) else Offset.Zero
        offset = offset * applied + fromCenter * (1f - applied) + pan
        scale = newScale
        clamp(size)
    }

    /** Keeps [offset] to the overhang the zoom made, so the photo cannot be dragged off the screen. */
    private fun clamp(size: IntSize) {
        val limitX = size.width * (scale - 1f) / 2f
        val limitY = size.height * (scale - 1f) / 2f
        offset = Offset(offset.x.coerceIn(-limitX, limitX), offset.y.coerceIn(-limitY, limitY))
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState
    data class Loaded(val bitmap: Bitmap) : PhotoState
    data object Failed : PhotoState
}

/**
 * One file of the viewer. Without a [zoom] it is not the page in front and
 * takes no gestures of its own, so the pager has them all. A video shows its
 * still with a play button over it. [next] is decoded ahead once this one is up.
 */
@Composable
private fun PhotoPage(path: String, zoom: PhotoZoom?, next: String?, onTap: () -> Unit) {
    val video = isVideo(path)
    val state by produceState<PhotoState>(initialValue = PhotoState.Loading, key1 = path) {
        // A video's still is not kept: it is cancelled when paged past, and a
        // kept one would hold that up.
        val bitmap = if (video) {
            withContext(Dispatchers.IO) { withCancellationSignal { decodeForScreen(path, it) } }
        } else {
            loadPhoto(path).await()
        }
        value = if (bitmap != null) PhotoState.Loaded(bitmap) else PhotoState.Failed
        // Only once this one is up, so the two do not share the time it takes.
        if (next != null && !isVideo(next)) loadPhoto(next)
    }
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tap ->
                        val z = currentZoom ?: return@detectTapGestures
                        // Zoomed in on the spot tapped, not the middle.
                        if (z.scale > 1f) z.reset() else z.transform(DOUBLE_TAP_ZOOM, Offset.Zero, tap, size)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val z = currentZoom
                        val pinching = event.changes.count { it.pressed } > 1
                        // One finger on an unzoomed photo is the pager's: left
                        // alone, it turns the page.
                        if (z != null && (pinching || z.scale > 1f)) {
                            z.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(), size)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            PhotoState.Loading -> CircularProgressIndicator(color = Color.White)
            is PhotoState.Loaded -> Image(
                bitmap = s.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val z = zoom
                        if (z != null) {
                            scaleX = z.scale
                            scaleY = z.scale
                            translationX = z.offset.x
                            translationY = z.offset.y
                        }
                    },
            )
            // A video with no still can still be played; the button says enough.
            PhotoState.Failed -> if (!video) Text(stringResource(R.string.photo_load_failed), color = Color.White)
        }
        if (video) {
            val context = LocalContext.current
            PlayButton(onClick = { playVideo(context, path) })
        }
    }
}

/** The one control a video gets for now: playing it is another app's job. */
@Composable
private fun PlayButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(PLAY_BUTTON),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, stringResource(R.string.play_video), Modifier.size(PLAY_ICON))
    }
}

/** Hands the video at [path] to whichever player the user has, through the
 * app's FileProvider: another app cannot read a plain path of ours. */
private fun playVideo(context: Context, path: String) {
    val file = File(path)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "video/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show()
    }
}

// ---------- Decoded photos ----------

/** Decoded photos by path, bounded by bytes: paging back shows the last few at
 * once, and the next one is ready before it is paged to. */
private val photoCache = bitmapCache(memoryShare = 4)

/** Decodes under way by path, so the photo paged to while it is still being
 * read ahead waits for that decode instead of starting a second. Main thread only. */
private val photoLoads = HashMap<String, Deferred<Bitmap?>>()

/** Not tied to a screen: a photo decode cannot be stopped part-way anyway, and
 * one read ahead is still wanted after the page that asked for it is gone. */
private val photoScope = MainScope()

/** [path] decoded to fit [PHOTO_MAX_DIMENSION], about a screen's worth: what the viewer shows. */
private fun decodeForScreen(path: String, signal: CancellationSignal): Bitmap? =
    decodeSampled(path, PHOTO_MAX_DIMENSION, signal, fit = true)

/** The photo at [path] fitted to [PHOTO_MAX_DIMENSION], from [photoCache] or decoded once into it. */
private fun loadPhoto(path: String): Deferred<Bitmap?> {
    photoCache.get(path)?.let { return CompletableDeferred(it) }
    return photoLoads.getOrPut(path) {
        photoScope.async {
            try {
                withContext(Dispatchers.IO) { decodeForScreen(path, CancellationSignal()) }
                    ?.also { photoCache.put(path, it) }
            } finally {
                photoLoads.remove(path)
            }
        }
    }
}
