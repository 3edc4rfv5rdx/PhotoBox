package xx.photobox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MouseDragTest {
    @Test
    fun aPullPastTheThresholdTurnsOnePage() {
        assertEquals(4, mousePageTarget(startPage = 3, position = 3.2f, pageCount = 10))
        assertEquals(2, mousePageTarget(startPage = 3, position = 2.8f, pageCount = 10))
        // Pulled most of the way, still one page: a pull is a swipe, not a scroll.
        assertEquals(4, mousePageTarget(startPage = 3, position = 4.9f, pageCount = 10))
    }

    @Test
    fun aShortPullGoesBack() {
        assertEquals(3, mousePageTarget(startPage = 3, position = 3.1f, pageCount = 10))
        assertEquals(3, mousePageTarget(startPage = 3, position = 2.9f, pageCount = 10))
    }

    @Test
    fun noPageBeyondTheEnds() {
        assertEquals(9, mousePageTarget(startPage = 9, position = 9.4f, pageCount = 10))
        assertEquals(0, mousePageTarget(startPage = 0, position = -0.4f, pageCount = 10))
        assertEquals(0, mousePageTarget(startPage = 0, position = 0.5f, pageCount = 1))
    }

    @Test
    fun whatIsUnderThePointerGoesWithIt() {
        // Dragged up, the list scrolls on; in a reversed layout the other way.
        assertEquals(30f, mouseScrollDelta(-30f, reverse = false))
        assertEquals(-30f, mouseScrollDelta(-30f, reverse = true))
    }
}
