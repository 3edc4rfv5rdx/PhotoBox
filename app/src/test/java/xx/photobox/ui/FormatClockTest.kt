package xx.photobox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatClockTest {
    @Test
    fun minutesAndSecondsUnderAnHour() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:59", formatClock(59_999))
        assertEquals("1:05", formatClock(65_000))
        assertEquals("59:59", formatClock(3_599_000))
    }

    @Test
    fun hoursFromAnHourUp() {
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("2:03:04", formatClock(7_384_000))
    }

    @Test
    fun anUnknownTimeReadsAsZero() {
        assertEquals("0:00", formatClock(-1))
    }
}
