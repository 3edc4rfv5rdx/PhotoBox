package xx.photobox.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.CancellationSignal
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
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.VerticalPager
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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import xx.photobox.AppSettings
import xx.photobox.MediaItem
import xx.photobox.R
import xx.photobox.Transfer
import xx.photobox.canRotate
import xx.photobox.VideoPlayer
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
 * tap shows or hides the bar on top, whose ⋮ has the file's info, and sends
 * it to another app through [onShare], turns it through [onRotate] when it
 * can be, renames it through [onRename], sends
 * it to another folder through [onTransfer] or to the trash through
 * [onDelete], when there are those. [onPage] hears each file settled on, so
 * the caller can open on it again after the list is reloaded.
 */
@Composable
fun ViewerScreen(
    sorted: List<MediaItem>,
    startPath: String,
    onPage: (String) -> Unit,
    onShare: ((MediaItem) -> Unit)?,
    onRotate: ((MediaItem) -> Unit)?,
    onRename: ((MediaItem) -> Unit)?,
    onTransfer: ((MediaItem, Transfer) -> Unit)?,
    onDelete: ((MediaItem) -> Unit)?,
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
    val context = LocalContext.current
    val videoPlayer by AppSettings.videoPlayer.collectAsState()
    // The video playing in the app's own player, over the viewer.
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // The file whose info is open.
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    val rotation = rememberMediaRotation()

    // A new page, or the photo turned under the fingers: either way it opens unzoomed.
    LaunchedEffect(pagerState.currentPage, rotation) { zoom.reset() }
    LaunchedEffect(pagerState.settledPage) {
        sorted.getOrNull(pagerState.settledPage)?.let { onPage(it.file.path) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val pageContent: @Composable PagerScope.(Int) -> Unit = { page ->
            val path = sorted[page].file.path
            PhotoPage(
                path = path,
                rotation = rotation,
                zoom = if (page == pagerState.currentPage) zoom else null,
                next = sorted.getOrNull(page + 1)?.file?.path,
                onTap = { chrome = !chrome },
                onPlay = {
                    when (videoPlayer) {
                        VideoPlayer.BUILT_IN -> playing = path
                        VideoPlayer.EXTERNAL -> playVideo(context, path)
                    }
                },
            )
        }
        // The swipe runs along the photo's own width, whichever way the phone
        // is held: the next file comes in from the photo's right, which on the
        // glass is below at 90, above at 270 and on the left at 180.
        when (rotation) {
            90, 270 -> VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
                reverseLayout = rotation == 270,
                key = { sorted[it].file.path },
                modifier = Modifier.fillMaxSize(),
                pageContent = pageContent,
            )

            else -> HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
                reverseLayout = rotation == 180,
                key = { sorted[it].file.path },
                modifier = Modifier.fillMaxSize(),
                pageContent = pageContent,
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
                    modifier = Modifier.padding(end = 4.dp),
                )
                Box {
                    MoreButton(onClick = { menuOpen = true }, tint = Color.White)
                    AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        AppMenuItem(stringResource(R.string.info), enabled = current != null) {
                            menuOpen = false
                            info = current?.file?.path
                        }
                        if (onShare != null) {
                            AppMenuItem(stringResource(R.string.share), enabled = current != null) {
                                menuOpen = false
                                current?.let(onShare)
                            }
                        }
                        if (onRotate != null) {
                            AppMenuItem(stringResource(R.string.rotate), enabled = current != null && canRotate(current.file)) {
                                menuOpen = false
                                current?.let(onRotate)
                            }
                        }
                        if (onRename != null) {
                            AppMenuItem(stringResource(R.string.rename), enabled = current != null) {
                                menuOpen = false
                                current?.let(onRename)
                            }
                        }
                        if (onTransfer != null) {
                            AppMenuItem(stringResource(R.string.move), enabled = current != null) {
                                menuOpen = false
                                current?.let { onTransfer(it, Transfer.MOVE) }
                            }
                            AppMenuItem(stringResource(R.string.copy), enabled = current != null) {
                                menuOpen = false
                                current?.let { onTransfer(it, Transfer.COPY) }
                            }
                        }
                        if (onDelete != null) {
                            AppMenuItem(stringResource(R.string.to_trash), enabled = current != null) {
                                menuOpen = false
                                current?.let(onDelete)
                            }
                        }
                    }
                }
            }
        }

        playing?.let { VideoPlayerScreen(it, onClose = { playing = null }) }
        info?.let { InfoDialog(File(it), onDismiss = { info = null }) }
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
 * still with a play button over it, which calls [onPlay]. [next] is decoded
 * ahead once this one is up.
 */
@Composable
private fun PhotoPage(
    path: String,
    rotation: Int,
    zoom: PhotoZoom?,
    next: String?,
    onTap: () -> Unit,
    onPlay: () -> Unit,
) {
    val video = isVideo(path)
    val state by produceState<PhotoState>(initialValue = PhotoState.Loading, key1 = photoKey(path)) {
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
            // The zoom outside the turn: gestures are measured on the glass,
            // so the zoom has to move the photo on the glass too.
            is PhotoState.Loaded -> Box(
                Modifier
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
            ) {
                RotatedFrame(rotation, Modifier.fillMaxSize()) {
                    Image(
                        bitmap = s.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // A video with no still can still be played; the button says enough.
            PhotoState.Failed -> if (!video) Text(stringResource(R.string.photo_load_failed), color = Color.White)
        }
        if (video) PlayButton(onClick = onPlay)
    }
}

/** The one control a video gets in the viewer: playing is the player's job. */
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

/** Hands the video at [path] to whichever player the user has. */
private fun playVideo(context: Context, path: String) {
    val file = File(path)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri(context, file), mimeTypeOf(file, "video/*"))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show()
    }
}

// ---------- Decoded photos ----------

/** Decoded photos by [photoKey], bounded by bytes: paging back shows the last few at
 * once, and the next one is ready before it is paged to. */
private val photoCache = bitmapCache(memoryShare = 4)

/** Decodes under way by [photoKey], so the photo paged to while it is still being
 * read ahead waits for that decode instead of starting a second. Main thread only. */
private val photoLoads = HashMap<String, Deferred<Bitmap?>>()

/** Not tied to a screen: a photo decode cannot be stopped part-way anyway, and
 * one read ahead is still wanted after the page that asked for it is gone. */
private val photoScope = MainScope()

/** [path] decoded to fit [PHOTO_MAX_DIMENSION], about a screen's worth: what the viewer shows. */
private fun decodeForScreen(path: String, signal: CancellationSignal): Bitmap? =
    decodeSampled(path, PHOTO_MAX_DIMENSION, signal, fit = true)

/** The photo at [path] fitted to [PHOTO_MAX_DIMENSION], from [photoCache] or decoded once into it. */
internal fun loadPhoto(path: String): Deferred<Bitmap?> {
    val key = photoKey(path)
    photoCache.get(key)?.let { return CompletableDeferred(it) }
    return photoLoads.getOrPut(key) {
        photoScope.async {
            try {
                withContext(Dispatchers.IO) { decodeForScreen(path, CancellationSignal()) }
                    ?.also { photoCache.put(key, it) }
            } finally {
                photoLoads.remove(key)
            }
        }
    }
}
