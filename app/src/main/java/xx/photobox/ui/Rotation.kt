package xx.photobox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import xx.photobox.AppSettings
import xx.photobox.MediaRotation
import xx.photobox.rememberDeviceRotation

/**
 * How far photos, videos and the slideshow are turned on the screen,
 * clockwise in quarter turns: the phone's tilt when [AppSettings.mediaRotation]
 * lets them turn now, else 0.
 */
@Composable
fun rememberMediaRotation(): Int {
    val setting by AppSettings.mediaRotation.collectAsState()
    val turns = when (setting) {
        MediaRotation.SENSOR -> true
        MediaRotation.SYSTEM -> rememberSystemAutoRotate()
        MediaRotation.NEVER -> false
    }
    return if (turns) rememberDeviceRotation() else 0
}

/**
 * The system's auto-rotate switch, followed while on screen: flipped in the
 * quick settings over the app, it takes effect at once.
 */
@Composable
private fun rememberSystemAutoRotate(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    var on by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                on = read()
            }
        }
        val uri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
        resolver.registerContentObserver(uri, false, observer)
        on = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return on
}

/**
 * [content] laid out in the space [modifier] gives it, turned by [rotation].
 * At 90 and 270 it is laid out with the sides swapped, so once turned it
 * still fills the space edge to edge.
 */
@Composable
fun RotatedFrame(rotation: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val turned = rotation == 90 || rotation == 270
        val frame = if (turned) Modifier.requiredSize(width = maxHeight, height = maxWidth) else Modifier.fillMaxSize()
        Box(
            frame.graphicsLayer { rotationZ = rotation.toFloat() },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
