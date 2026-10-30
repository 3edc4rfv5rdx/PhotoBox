package xx.photobox.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xx.photobox.MediaItem
import xx.photobox.R
import java.io.File

private const val FADE_MS = 1000

/** How long a slideshow waits after Go on before it moves on, unless its own time is shorter. */
private const val RESUME_WAIT_MS = 3000L

private val CONTROLS_GAP = 12.dp
private val CONTROLS_MIDDLE_GAP = 20.dp
private val CONTROLS_CORNER = 28.dp

/** How long the controls stay up after the last touch. */
private const val CONTROLS_SHOWN_MS = 3000L

/** One photo up on the slideshow. A class of its own, so the same file shown again still counts as a new slide. */
private class Slide(val path: String, val bitmap: Bitmap)

/**
 * [photos] one after another from [start], round and round, each held
 * [seconds] and faded into the next in the middle of the screen. The screen
 * stays on and the system bars are hidden while it runs. A tap shows the
 * controls — back to leave at the top; previous, info, pause or go on and
 * next in a block at the bottom — which hide again a few
 * seconds after the last touch; Back stops it too. Going on from a pause moves
 * to the next photo after [RESUME_WAIT_MS], not a whole [seconds]. Each photo put up is
 * reported to [onShown], so the next start can go on from it. A photo that
 * cannot be decoded is passed over in the direction being moved, and when
 * none can, it ends by itself.
 */
@Composable
fun SlideshowScreen(
    photos: List<MediaItem>,
    start: Int,
    seconds: Int,
    onShown: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    KeepScreenOn()
    HideSystemBars()
    val rotation = rememberMediaRotation()
    var index by rememberSaveable { mutableIntStateOf(start.coerceIn(photos.indices)) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // The photo whose info is open; the show waits while it is.
    var info by remember { mutableStateOf<String?>(null) }
    // Just gone on from a pause: the photo up has been looked at long enough already.
    var resuming by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    // Counted up with each touch of the controls, so their hiding starts over.
    var touched by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<Slide?>(null) }
    // Which way a photo that cannot be decoded is passed over: the way the last move went.
    var step by remember { mutableIntStateOf(1) }
    var failedInARow by remember { mutableIntStateOf(0) }
    val currentSeconds by rememberUpdatedState(seconds)
    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnClose by rememberUpdatedState(onClose)

    fun move(by: Int) {
        resuming = false
        step = by
        index = Math.floorMod(index + by, photos.size)
    }

    // Puts up the photo at index, or passes it over.
    LaunchedEffect(index, photos) {
        // A reloaded album can be shorter than where the slideshow had got to.
        if (index !in photos.indices) {
            index = 0
            return@LaunchedEffect
        }
        val path = photos[index].file.path
        val bitmap = loadPhoto(path).await()
        if (bitmap == null) {
            failedInARow++
            if (failedInARow >= photos.size) currentOnClose() else move(step)
            return@LaunchedEffect
        }
        failedInARow = 0
        // A single photo is not faded into itself.
        if (shown?.path != path) shown = Slide(path, bitmap)
        currentOnShown(path)
        // Read ahead while this one is up, so the fade does not wait on a decode.
        loadPhoto(photos[Math.floorMod(index + step, photos.size)].file.path)
    }
    // Holds the photo up, then moves on; a move by hand, a pause or a new photo
    // starts it over. Not while the photo moved to is still being read: the
    // one still up would otherwise move the show on a second time.
    LaunchedEffect(shown, index, paused, info) {
        if (paused || info != null || shown == null || shown?.path != photos.getOrNull(index)?.file?.path) return@LaunchedEffect
        val hold = currentSeconds * 1000L
        delay(if (resuming) minOf(hold, RESUME_WAIT_MS) else hold)
        move(1)
    }
    LaunchedEffect(controls, touched, info) {
        if (!controls || info != null) return@LaunchedEffect
        delay(CONTROLS_SHOWN_MS)
        controls = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controls = !controls } },
        contentAlignment = Alignment.Center,
    ) {
        RotatedFrame(rotation, Modifier.fillMaxSize()) {
            Crossfade(targetState = shown, animationSpec = tween(FADE_MS), label = "slide") { slide ->
                if (slide != null) {
                    Image(
                        bitmap = slide.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }

        if (controls) {
            fun touch(action: () -> Unit): () -> Unit = {
                touched++
                action()
            }
            // Back to leave at the top, as everywhere else; the rest one block
            // at the bottom, the moves at its two ends.
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                OverlayIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.back),
                    onClose,
                    Modifier.align(Alignment.TopStart),
                )
                // A compact block, not the screen's width: the buttons sit close
                // under one thumb, the middle pair a little further apart.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(CONTROLS_CORNER))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LargeIconButton(
                        Icons.Filled.SkipPrevious,
                        stringResource(R.string.slideshow_previous),
                        touch { move(-1) },
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(CONTROLS_GAP))
                    LargeIconButton(
                        Icons.Outlined.Info,
                        stringResource(R.string.info),
                        touch { info = shown?.path },
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(CONTROLS_MIDDLE_GAP))
                    LargeIconButton(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(if (paused) R.string.slideshow_resume else R.string.pause),
                        touch {
                            resuming = paused
                            paused = !paused
                        },
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(CONTROLS_GAP))
                    LargeIconButton(
                        Icons.Filled.SkipNext,
                        stringResource(R.string.slideshow_next),
                        touch { move(1) },
                        tint = Color.White,
                    )
                }
            }
        }

        info?.let { InfoDialog(File(it), onDismiss = { info = null }) }
    }
}
