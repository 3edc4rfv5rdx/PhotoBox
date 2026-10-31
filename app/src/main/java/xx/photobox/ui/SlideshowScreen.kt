package xx.photobox.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xx.photobox.MediaItem
import xx.photobox.R
import xx.photobox.SlideMotion
import xx.photobox.SlideOrder
import xx.photobox.albumOrder
import xx.photobox.albumOrderAt
import xx.photobox.longerSlideSeconds
import xx.photobox.shorterSlideSeconds
import xx.photobox.next
import xx.photobox.previous
import xx.photobox.randomSlideMotion
import xx.photobox.reconciled
import xx.photobox.shuffledOrder
import xx.photobox.startedOver
import xx.photobox.withShuffle
import java.io.File

private const val FADE_MS = 1000

/**
 * Runs the show's own animations at their real length even with the phone's
 * animations turned off: the fade and the motion are what a slideshow is, and
 * the motion is switched on by hand.
 */
private object FullMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/** How long a slideshow waits after Go on before it moves on, unless its own time is shorter. */
private const val RESUME_WAIT_MS = 3000L

private val CONTROLS_GAP = 12.dp
private val CONTROLS_MIDDLE_GAP = 20.dp
private val CONTROLS_CORNER = 28.dp

/** Previous, play and next: the buttons a show is driven by, a size up from the rest. */
private val DRIVE_BUTTON = 56.dp
private val DRIVE_ICON = 36.dp

/** How long the controls stay up after the last touch. */
private const val CONTROLS_SHOWN_MS = 3000L

/** The app's icon over the last photo once the show has ended. */
private val END_MARK = 160.dp
private const val END_MARK_ALPHA = 0.85f

/** The caption's room from the screen's edges. */
private val CAPTION_PADDING = 16.dp

/**
 * One photo up on the slideshow. A class of its own, so the same file shown
 * again still counts as a new slide — with a [motion] of its own.
 */
private class Slide(val path: String, val bitmap: Bitmap, val motion: SlideMotion = randomSlideMotion())

/**
 * [photos] one after another from [start] to the last, each held [seconds]
 * and faded into the next in the middle of the screen. After the last it
 * stops: the app's icon goes up over that photo, the controls stay up, and
 * Play starts it over from the first; [onEnded] hears it got there. The screen
 * stays on and the system bars are hidden while it runs. The controls are up
 * at the start, and a tap shows them again — back to leave at the top;
 * previous, the show's menu, pause or go on, info and next in a block at
 * the bottom — which hide again a few
 * seconds after the last touch; Back stops it too. The menu shuffles the
 * whole album from the photo up, each photo once, and repeats the show round
 * and round, a new shuffle each round; both start off every time. Going on
 * from a pause moves to the next photo after [RESUME_WAIT_MS], not a whole
 * [seconds]. Each photo put up in album order is reported to [onShown], so
 * the next start can go on from it; a shuffled one is not. With [caption]
 * each photo's date and name are written in its top corner, fading with it;
 * the menu switches it through [onCaption], and steps the time per photo
 * through [onSeconds], from the next photo on. With [motion] each photo
 * zooms slowly in or out and drifts across the middle, a way of its own
 * picked at random, standing still while the show waits; the menu switches
 * it through [onMotion]. A photo that
 * cannot be decoded is passed over in the direction being moved — forward
 * when there is nothing left the other way — and when none can, it closes by itself.
 */
@Composable
fun SlideshowScreen(
    photos: List<MediaItem>,
    start: Int,
    seconds: Int,
    onSeconds: (Int) -> Unit,
    caption: Boolean,
    onCaption: (Boolean) -> Unit,
    motion: Boolean,
    onMotion: (Boolean) -> Unit,
    onShown: (String) -> Unit,
    onEnded: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    KeepScreenOn()
    HideSystemBars()
    val rotation = rememberMediaRotation()
    val paths = remember(photos) { photos.map { it.file.path } }
    val taken = remember(photos) { photos.associate { it.file.path to it.taken } }
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
    // Up from the start, so the show's menu is at hand before the first photo moves on.
    var controls by remember { mutableStateOf(true) }
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
            SlideFade(shown) { slide ->
                if (slide != null) {
                    Box(Modifier.fillMaxSize()) {
                        // From 0 as the photo fades in to 1 as it is gone again, held still
                        // whenever the show waits; going on, it covers only what is left.
                        val progress = remember(slide) { Animatable(0f) }
                        val moving = motion && !paused && !ended && info == null
                        LaunchedEffect(slide, moving) {
                            if (!moving) return@LaunchedEffect
                            val total = currentSeconds * 1000 + FADE_MS
                            withContext(FullMotion) {
                                progress.animateTo(1f, tween(((1f - progress.value) * total).toInt(), easing = LinearEasing))
                            }
                        }
                        Image(
                            bitmap = slide.bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().then(
                                if (motion) {
                                    Modifier.graphicsLayer {
                                        val frame = slide.motion.at(progress.value)
                                        scaleX = frame.scale
                                        scaleY = frame.scale
                                        translationX = frame.x * size.width
                                        translationY = frame.y * size.height
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        )
                        if (caption) Caption(slide.path, taken[slide.path], Modifier.align(Alignment.TopEnd))
                    }
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
                // under one thumb, play in the middle a little further apart.
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
                        size = DRIVE_BUTTON,
                        iconSize = DRIVE_ICON,
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
                            AppMenuCheckItem(stringResource(R.string.slideshow_caption), caption) {
                                touched++
                                onCaption(!caption)
                            }
                            AppMenuCheckItem(stringResource(R.string.slideshow_motion), motion) {
                                touched++
                                onMotion(!motion)
                            }
                            SlideTimeLine(seconds) {
                                touched++
                                onSeconds(it)
                            }
                        }
                    }
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
                        size = DRIVE_BUTTON,
                        iconSize = DRIVE_ICON,
                    )
                    Spacer(Modifier.width(CONTROLS_MIDDLE_GAP))
                    LargeIconButton(
                        Icons.Outlined.Info,
                        stringResource(R.string.info),
                        touch { info = shown?.path },
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(CONTROLS_GAP))
                    LargeIconButton(
                        Icons.Filled.SkipNext,
                        stringResource(R.string.slideshow_next),
                        touch { move(1) },
                        tint = Color.White,
                        size = DRIVE_BUTTON,
                        iconSize = DRIVE_ICON,
                    )
                }
            }
        }

        info?.let { InfoDialog(File(it), onDismiss = { info = null }) }
    }
}

/**
 * [target] faded in over the slide before it, [FADE_MS] long whatever the
 * phone's animation setting. Each slide keeps its own composition — and so its
 * motion — as it goes from coming in to going out. A new target arriving mid-fade
 * fades in over the one that was coming in; the one before that goes at once.
 * [content] gets null for the wait before the first photo.
 */
@Composable
private fun SlideFade(target: Slide?, content: @Composable (Slide?) -> Unit) {
    var front by remember { mutableStateOf(target) }
    var back by remember { mutableStateOf<Slide?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target === front) return@LaunchedEffect
        back = front
        front = target
        fade.snapTo(0f)
        withContext(FullMotion) { fade.animateTo(1f, tween(FADE_MS)) }
        back = null
    }
    Box(Modifier.fillMaxSize()) {
        if (front == null) content(null)
        // One loop, so a slide moving from front to back keeps its composition.
        for (slide in listOfNotNull(back, front)) {
            key(slide) {
                val coming = slide === front
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (coming) fade.value else 1f - fade.value }) {
                    content(slide)
                }
            }
        }
    }
}

/**
 * A photo's date and name, white with a shadow so they read over any photo.
 * The date is the one the album sorts by, gone with a photo the album no
 * longer has.
 */
@Composable
private fun Caption(path: String, taken: Long?, modifier: Modifier) {
    val context = LocalContext.current
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = Color.White,
        shadow = Shadow(Color.Black, Offset(0f, 2f), blurRadius = 6f),
    )
    Column(modifier.padding(CAPTION_PADDING), horizontalAlignment = Alignment.End) {
        taken?.let { Text(formatDateTime(context, it), style = style, maxLines = 1) }
        Text(File(path).name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The show's menu line for the time per photo: shorter, the time, longer, one of the menu's steps a tap. */
@Composable
private fun SlideTimeLine(seconds: Int, onChange: (Int) -> Unit) {
    val shorter = shorterSlideSeconds(seconds)
    val longer = longerSlideSeconds(seconds)
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LargeIconButton(
            Icons.Filled.Remove,
            stringResource(R.string.slideshow_shorter),
            { shorter?.let(onChange) },
            enabled = shorter != null,
        )
        Text(
            stringResource(R.string.seconds_short, seconds),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        LargeIconButton(
            Icons.Filled.Add,
            stringResource(R.string.slideshow_longer),
            { longer?.let(onChange) },
            enabled = longer != null,
        )
    }
}
