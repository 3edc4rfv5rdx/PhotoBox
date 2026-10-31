package xx.photobox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTest {
    @Test
    fun halvesDownThenEndsAtTheSide() {
        val steps = backdropSteps(4000, 3000)
        assertEquals(BACKDROP_SIDE, maxOf(steps.last().first, steps.last().second))
        // Every step but the last is half the one before.
        var w = 4000
        var h = 3000
        for ((sw, sh) in steps.dropLast(1)) {
            assertEquals(w / 2, sw)
            assertEquals(h / 2, sh)
            w = sw
            h = sh
        }
        // The last never shrinks by more than half either, so no pixel is passed over.
        assertTrue(steps.last().first * 2 >= w && steps.last().second * 2 >= h)
    }

    @Test
    fun keepsTheShape() {
        val (w, h) = backdropSteps(1080, 1920).last()
        assertEquals(BACKDROP_SIDE, h)
        assertEquals(6, w)
    }

    @Test
    fun aThinPhotoKeepsAPixelAcross() {
        val (w, h) = backdropSteps(5000, 3).last()
        assertEquals(BACKDROP_SIDE, w)
        assertEquals(1, h)
    }

    @Test
    fun aSmallPhotoIsLeftAsItIs() {
        assertEquals(emptyList<Pair<Int, Int>>(), backdropSteps(10, 8))
        assertEquals(emptyList<Pair<Int, Int>>(), backdropSteps(BACKDROP_SIDE, BACKDROP_SIDE))
    }
}
