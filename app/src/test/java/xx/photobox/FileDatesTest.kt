package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FileDatesTest {
    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun exifDateIsTheCamerasWallClock() {
        assertEquals(local(2024, 3, 12, 14, 5, 7), parseExifDate("2024:03:12 14:05:07"))
    }

    @Test
    fun exifDateThatIsNotSetIsNoDate() {
        assertNull(parseExifDate(null))
        assertNull(parseExifDate(""))
        assertNull(parseExifDate("    :  :     :  :  "))
        assertNull(parseExifDate("0000:00:00 00:00:00"))
    }

    @Test
    fun videoDateIsUtcWithOrWithoutMilliseconds() {
        assertEquals(utc(2024, 3, 12, 14, 5, 7), parseVideoDate("20240312T140507.000Z"))
        assertEquals(utc(2024, 3, 12, 14, 5, 7), parseVideoDate("20240312T140507Z"))
    }

    @Test
    fun videoDateAtTheContainersZeroIsNoDate() {
        assertNull(parseVideoDate("19040101T000000.000Z"))
        assertNull(parseVideoDate(null))
        assertNull(parseVideoDate("garbage"))
    }
}
