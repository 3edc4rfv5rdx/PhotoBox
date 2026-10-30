package xx.photobox.ui

import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import xx.photobox.R
import java.io.File

/** How often the position under the seek bar is read while the video plays. */
private const val POSITION_TICK_MS = 200L

/**
 * The video at [path] in the app's own player, full screen, starting at
 * once. The picture turns with the phone like a photo does; the controls —
 * back on top, play or pause, the time and the seek bar below — stay upright
 * with the rest of the app, and a tap hides or shows them. Paused when the
 * app goes to the background, released when this closes.
 */
@Composable
fun VideoPlayerScreen(path: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    KeepScreenOn()
    val context = LocalContext.current
    val rotation = rememberMediaRotation()
    var failed by remember(path) { mutableStateOf(false) }
    var isPlaying by remember(path) { mutableStateOf(false) }
    var videoSize by remember(path) { mutableStateOf(VideoSize.UNKNOWN) }
    var position by remember(path) { mutableLongStateOf(0L) }
    var duration by remember(path) { mutableLongStateOf(0L) }
    // While the seek bar is held its own value shows, not the player's.
    var seeking by remember(path) { mutableStateOf<Float?>(null) }
    var controls by remember(path) { mutableStateOf(true) }

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LifecycleResumeEffect(player) {
        onPauseOrDispose { player.pause() }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(POSITION_TICK_MS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controls = !controls } },
    ) {
        RotatedFrame(rotation, Modifier.fillMaxSize()) {
            // A TextureView, not a SurfaceView: a surface is drawn outside the
            // app's own layers and would not turn with the frame around it.
            val aspect = if (videoSize.width > 0 && videoSize.height > 0) {
                videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            } else {
                null
            }
            AndroidView(
                factory = { viewContext -> TextureView(viewContext).also(player::setVideoTextureView) },
                modifier = if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(),
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.video_play_failed),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (controls) {
            Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.systemBars)) {
                LargeIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClose, tint = Color.White)
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LargeIconButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play_video),
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                        } else {
                            // Played to the end, it starts over.
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                            player.play()
                        }
                    },
                    tint = Color.White,
                )
                Text(
                    "${formatClock(position)} / ${formatClock(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Slider(
                    value = seeking ?: if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { seeking = it },
                    onValueChangeFinished = {
                        seeking?.let { player.seekTo((it * duration).toLong()) }
                        seeking = null
                    },
                    enabled = duration > 0,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
        }
    }
}
