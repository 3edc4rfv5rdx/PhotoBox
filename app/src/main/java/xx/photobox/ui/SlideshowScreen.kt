package xx.photobox.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import xx.photobox.MediaItem

private const val FADE_MS = 1000

/** One photo up on the slideshow. A class of its own, so the same file shown again still counts as a new slide. */
private class Slide(val path: String, val bitmap: Bitmap)

/**
 * [photos] one after another, round and round, each held [seconds] and
 * faded into the next in the middle of the screen. The screen stays on and
 * the system bars are hidden while it runs; a tap or Back ends it. A photo
 * that cannot be decoded is passed over, and when none can, it ends by itself.
 */
@Composable
fun SlideshowScreen(photos: List<MediaItem>, seconds: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    KeepScreenOn()
    HideSystemBars()
    val rotation = rememberMediaRotation()
    var shown by remember { mutableStateOf<Slide?>(null) }
    val currentSeconds by rememberUpdatedState(seconds)
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(photos) {
        var index = 0
        var failedInARow = 0
        while (failedInARow < photos.size) {
            val path = photos[index].file.path
            val next = (index + 1) % photos.size
            val bitmap = loadPhoto(path).await()
            index = next
            if (bitmap == null) {
                failedInARow++
                continue
            }
            failedInARow = 0
            // A single photo is not faded into itself every few seconds.
            if (shown?.path != path) shown = Slide(path, bitmap)
            // Read ahead while this one is up, so the fade does not wait on a decode.
            loadPhoto(photos[next].file.path)
            delay(currentSeconds * 1000L)
        }
        currentOnClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { currentOnClose() } },
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
    }
}

