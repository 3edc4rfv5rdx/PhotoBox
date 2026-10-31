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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xx.photobox.MediaItem
import xx.photobox.R
import xx.photobox.SlideOrder
import xx.photobox.albumOrder
import xx.photobox.albumOrderAt
import xx.photobox.next
import xx.photobox.previous
import xx.photobox.reconciled
import xx.photobox.shuffledOrder
import xx.photobox.startedOver
import xx.photobox.withShuffle
import java.io.File

private const val FADE_MS = 1000

/** How long a slideshow waits after Go on before it moves on, unless its own time is shorter. */
private const val RESUME_WAIT_MS = 3000L

private val CONTROLS_GAP = 12.dp
private val CONTROLS_MIDDLE_GAP = 20.dp
private val CONTROLS_CORNER = 28.dp

/** How long the controls stay up after the last touch. */
private const val CONTROLS_SHOWN_MS = 3000L

/** The app's icon over the last photo once the show has ended. */
private val END_MARK = 160.dp
private const val END_MARK_ALPHA = 0.85f

/** One photo up on the slideshow. A class of its own, so the same file shown again still counts as a new slide. */
private class Slide(val path: String, val bitmap: Bitmap)

/**
 * [photos] one after another from [start] to the last, each held [seconds]
 * and faded into the next in the middle of the screen. After the last it
 * stops: the app's icon goes up over that photo, the controls stay up, and
 * Play starts it over from the first; [onEnded] hears it got there. The screen
 * stays on and the system bars are hidden while it runs. A tap shows the
 * controls — back to leave at the top; previous, the show's menu, info, pause
 * or go on and next in a block at the bottom — which hide again a few
 * seconds after the last touch; Back stops it too. The menu shuffles the
 * whole album from the photo up, each photo once, and repeats the show round
 * and round, a new shuffle each round; both start off every time. Going on
 * from a pause moves to the next photo after [RESUME_WAIT_MS], not a whole
 * [seconds]. Each photo put up in album order is reported to [onShown], so
 * the next start can go on from it; a shuffled one is not. A photo that
 * cannot be decoded is passed over in the direction being moved — forward
 * when there is nothing left the other way — and when none can, it closes by itself.
 */
@Composable
fun SlideshowScreen(
    photos: List<MediaItem>,
    start: Int,
    seconds: Int,
    onShown: (String) -> Unit,
    onEnded: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    KeepScreenOn()
    HideSystemBars()
    val rotation = rememberMediaRotation()
    val paths = remember(photos) { photos.map { it.file.path } }
    val currentPaths by rememberUpdatedState(paths)
    // Saved as the photo up and whether shuffled: a recreated show goes on from
    // it, a shuffled one in a new round, rather than keeping a whole album's
    // order in the saved state.
    var order by rememberSaveable(
        stateSaver = listSaver<SlideOrder, Any?>(
            save = { listOf(it.current, it.shuffled) },
            restore = { saved ->
                val at = saved[0] as String?
                if (saved[1] as Boolean) shuffledOrder(currentPaths, at) else albumOrderAt(currentPaths, at)
            },
        ),
    ) { mutableStateOf(albumOrder(paths, start)) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    // Past the last photo: nothing moves on until Play starts over.
    var ended by rememberSaveable { mutableStateOf(false) }
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
    val currentOnEnded by rememberUpdatedState(onEnded)

    fun end() {
        ended = true
        controls = true
        currentOnEnded()
    }

    // Forward past the last photo ends the show unless it repeats; back from the first stays on it.
    fun move(by: Int) {
        resuming = false
        step = by
        ended = false
        val moved = if (by > 0) order.next(repeat, paths) else order.previous()
        if (moved == null) end() else order = moved
    }

    fun startOver() {
        resuming = false
        paused = false
        ended = false
        step = 1
        order = order.startedOver(paths)
    }

    // The middle button: pause, go on, or at the end start over.
    fun playPause() {
        if (ended) {
            startOver()
        } else {
            resuming = paused
            paused = !paused
        }
    }

    // A reloaded album: the order follows it, staying on the photo up.
    LaunchedEffect(paths) { order = order.reconciled(paths) }
    val pos = order.pos
    val at = order.current
    // Puts up the photo at pos, or passes it over.
    LaunchedEffect(pos, at) {
        val path = at ?: return@LaunchedEffect
        val bitmap = loadPhoto(path).await()
        if (bitmap == null) {
            failedInARow++
            when {
                failedInARow >= paths.size -> currentOnClose()
                // Nothing further back to pass over to.
                step < 0 && pos == 0 -> move(1)
                else -> move(step)
            }
            return@LaunchedEffect
        }
        failedInARow = 0
        // A single photo is not faded into itself.
        if (shown?.path != path) shown = Slide(path, bitmap)
        // The place to go on from is a place in the album, which a shuffled round is not.
        if (!order.shuffled) currentOnShown(path)
        // Read ahead while this one is up, so the fade does not wait on a decode.
        order.paths.getOrNull(pos + step)?.let { loadPhoto(it) }
    }
    // Holds the photo up, then moves on; a move by hand, a pause or a new photo
    // starts it over. Not while the photo moved to is still being read: the
    // one still up would otherwise move the show on a second time.
    LaunchedEffect(shown, pos, at, paused, info, ended) {
        if (ended || paused || info != null || shown == null || shown?.path != at) return@LaunchedEffect
        val hold = currentSeconds * 1000L
        delay(if (resuming) minOf(hold, RESUME_WAIT_MS) else hold)
        move(1)
    }
    // At the end they stay: there is nothing else to look at; and under the open menu.
    LaunchedEffect(controls, touched, info, ended, menuOpen) {
        if (!controls || info != null || ended || menuOpen) return@LaunchedEffect
        delay(CONTROLS_SHOWN_MS)
        controls = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controls = !controls } }
            // From a remote: the arrows step, OK pauses, goes on, or starts over at the end.
            .remoteKeys(
                onLeft = { move(-1) },
                onRight = { move(1) },
                onOk = {
                    controls = true
                    touched++
                    playPause()
                },
            ),
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
            if (ended) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    alpha = END_MARK_ALPHA,
                    modifier = Modifier.align(Alignment.Center).size(END_MARK),
                )
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
                    // Stays open while its lines are switched, so both can be set at once.
                    Box {
                        LargeIconButton(
                            Icons.Outlined.Settings,
                            stringResource(R.string.slideshow_options),
                            touch { menuOpen = true },
                            tint = Color.White,
                        )
                        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuCheckItem(stringResource(R.string.slideshow_shuffle), order.shuffled) {
                                touched++
                                order = order.withShuffle(!order.shuffled, paths)
                            }
                            AppMenuCheckItem(stringResource(R.string.slideshow_repeat), repeat) {
                                touched++
                                repeat = !repeat
                            }
                        }
                    }
                    Spacer(Modifier.width(CONTROLS_GAP))
                    LargeIconButton(
                        Icons.Outlined.Info,
                        stringResource(R.string.info),
                        touch { info = shown?.path },
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(CONTROLS_MIDDLE_GAP))
                    LargeIconButton(
                        if (paused || ended) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(
                            when {
                                ended -> R.string.slideshow_from_start
                                paused -> R.string.slideshow_resume
                                else -> R.string.pause
                            },
                        ),
                        touch(::playPause),
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
