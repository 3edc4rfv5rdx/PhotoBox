package xx.photobox

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The least a moving slide is zoomed: what leaves the room for it to be moved about in. */
const val MOTION_MIN_SCALE = 1.1f

/** The most a moving slide is zoomed. */
const val MOTION_MAX_SCALE = 1.25f

/** How far a moving slide goes either side of the middle, as a part of the frame: as far as [MOTION_MIN_SCALE] leaves room. */
const val MOTION_SHIFT = (MOTION_MIN_SCALE - 1f) / 2f

/** Where a moving slide is at one moment: its zoom, and its shift as parts of the frame's width and height. */
data class MotionFrame(val scale: Float, val x: Float, val y: Float)

/**
 * A slide's slow zoom and drift, from the start of its time up to the end:
 * zooming in or out, and going across the middle along [angle], in radians.
 */
data class SlideMotion(val zoomIn: Boolean, val angle: Double) {
    /** Where the slide is at [t], from 0 at the start to 1 at the end. */
    fun at(t: Float): MotionFrame {
        val p = t.coerceIn(0f, 1f)
        val zoom = if (zoomIn) p else 1f - p
        val along = (2f * p - 1f) * MOTION_SHIFT
        return MotionFrame(
            scale = MOTION_MIN_SCALE + (MOTION_MAX_SCALE - MOTION_MIN_SCALE) * zoom,
            x = along * cos(angle).toFloat(),
            y = along * sin(angle).toFloat(),
        )
    }
}

/** A motion of its own for each slide: in or out, any way across. */
fun randomSlideMotion(random: Random = Random): SlideMotion =
    SlideMotion(zoomIn = random.nextBoolean(), angle = random.nextDouble(0.0, 2 * PI))
