package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SlideMotionTest {
    private val steps = (0..100).map { it / 100f }

    @Test
    fun neverShowsPastThePhotosEdges() {
        val random = Random(1)
        repeat(500) {
            val motion = randomSlideMotion(random)
            for (t in steps) {
                val frame = motion.at(t)
                val room = (frame.scale - 1f) / 2f + 1e-6f
                assertTrue("x at $t of $motion", abs(frame.x) <= room)
                assertTrue("y at $t of $motion", abs(frame.y) <= room)
                assertTrue(frame.scale in MOTION_MIN_SCALE..MOTION_MAX_SCALE)
            }
        }
    }

    @Test
    fun zoomsTheWayItWasPicked() {
        val zoomIn = SlideMotion(zoomIn = true, angle = 0.0)
        assertEquals(MOTION_MIN_SCALE, zoomIn.at(0f).scale, 1e-6f)
        assertEquals(MOTION_MAX_SCALE, zoomIn.at(1f).scale, 1e-6f)
        val zoomOut = SlideMotion(zoomIn = false, angle = 0.0)
        assertEquals(MOTION_MAX_SCALE, zoomOut.at(0f).scale, 1e-6f)
        assertEquals(MOTION_MIN_SCALE, zoomOut.at(1f).scale, 1e-6f)
    }

    @Test
    fun driftsAcrossTheMiddleAlongItsAngle() {
        val right = SlideMotion(zoomIn = true, angle = 0.0)
        assertEquals(-MOTION_SHIFT, right.at(0f).x, 1e-6f)
        assertEquals(0f, right.at(0.5f).x, 1e-6f)
        assertEquals(MOTION_SHIFT, right.at(1f).x, 1e-6f)
        assertEquals(0f, right.at(1f).y, 1e-6f)
    }

    @Test
    fun outsideItsTimeItStaysAtItsEnds() {
        val motion = SlideMotion(zoomIn = true, angle = 1.0)
        assertEquals(motion.at(0f), motion.at(-1f))
        assertEquals(motion.at(1f), motion.at(2f))
    }

    @Test
    fun picksEveryWay() {
        val random = Random(2)
        val motions = List(400) { randomSlideMotion(random) }
        assertTrue(motions.any { it.zoomIn } && motions.any { !it.zoomIn })
        // All four quarters of the circle come up.
        val quarters = motions.map { (it.angle / (Math.PI / 2)).toInt() }.toSet()
        assertEquals(setOf(0, 1, 2, 3), quarters)
    }
}
