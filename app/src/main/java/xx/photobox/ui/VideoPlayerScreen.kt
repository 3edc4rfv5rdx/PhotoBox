package xx.photobox.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import xx.photobox.R
import java.io.File

/**
 * The video at [path] in the app's own player, full screen, starting at
 * once, with the player's own controls. Paused when the app goes to the
 * background, released when this closes.
 */
@Composable
fun VideoPlayerScreen(path: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var failed by remember(path) { mutableStateOf(false) }
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    // The screen stays on while the video is up, not only while it plays.
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            Text(
                stringResource(R.string.video_play_failed),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            LargeIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClose, tint = Color.White)
        }
    }
}
