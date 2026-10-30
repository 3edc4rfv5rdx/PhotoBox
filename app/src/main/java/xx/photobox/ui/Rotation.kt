package xx.photobox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import xx.photobox.AppSettings
import xx.photobox.rememberDeviceRotation

/**
 * How far photos, videos and the slideshow are turned on the screen,
 * clockwise in quarter turns: the phone's tilt while the setting is on, else 0.
 */
@Composable
fun rememberMediaRotation(): Int {
    val enabled by AppSettings.sensorRotation.collectAsState()
    return if (enabled) rememberDeviceRotation() else 0
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
