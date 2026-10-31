package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideSecondsTest {
    @Test
    fun stepsLieInTheSliderRangeInOrder() {
        assertTrue(SLIDE_SECONDS_STEPS.all { it in SLIDE_SECONDS })
        assertEquals(SLIDE_SECONDS_STEPS.sorted().distinct(), SLIDE_SECONDS_STEPS)
        assertEquals(SLIDE_SECONDS.first, SLIDE_SECONDS_STEPS.first())
        assertEquals(SLIDE_SECONDS.last, SLIDE_SECONDS_STEPS.last())
        assertTrue(DEFAULT_SLIDE_SECONDS in SLIDE_SECONDS_STEPS)
    }

    @Test
    fun stepsFromOneOfThem() {
        assertEquals(7, longerSlideSeconds(5))
        assertEquals(3, shorterSlideSeconds(5))
    }

    @Test
    fun stepsFromATimeBetweenThemGoToTheNeighbours() {
        assertEquals(7, longerSlideSeconds(6))
        assertEquals(5, shorterSlideSeconds(6))
    }

    @Test
    fun noStepPastTheEnds() {
        assertNull(shorterSlideSeconds(1))
        assertNull(longerSlideSeconds(30))
    }
}
