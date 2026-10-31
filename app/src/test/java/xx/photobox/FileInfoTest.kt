package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileInfoTest {
    @Test
    fun cameraNameDropsAMakeTheModelRepeats() {
        assertEquals("Canon EOS R6", cameraName("Canon", "Canon EOS R6"))
        assertEquals("Google Pixel 8", cameraName("Google", "Pixel 8"))
        assertEquals("Pixel 8", cameraName(null, " Pixel 8 "))
        assertEquals("Google", cameraName("Google", ""))
        assertNull(cameraName(" ", null))
    }

    @Test
    fun exposureUnderASecondIsAFraction() {
        assertEquals("1/125", exposureText(0.008))
        assertEquals("1/3", exposureText(1.0 / 3))
        assertEquals("1", exposureText(1.0))
        assertEquals("2.5", exposureText(2.5))
    }

    @Test
    fun numbersKeepOneDecimalAtMost() {
        assertEquals("1.8", trimmedNumber(1.8))
        assertEquals("4", trimmedNumber(4.0))
        assertEquals("26.5", trimmedNumber(26.46))
    }

    @Test
    fun iso6709PlaceParsesWithOrWithoutAltitude() {
        assertEquals(37.4219 to -122.084, parseIso6709("+37.4219-122.0840/"))
        assertEquals(-33.8688 to 151.2093, parseIso6709("-33.8688+151.2093+010.000/"))
        assertNull(parseIso6709(null))
        assertNull(parseIso6709("nowhere"))
        assertNull(parseIso6709("+95.0+10.0/"))
    }

    @Test
    fun megapixelsToOneDecimal() {
        assertEquals("9.6", megapixelsText(4624, 2084))
        assertEquals("12", megapixelsText(4000, 3000))
        assertEquals("0.1", megapixelsText(320, 240))
        assertNull(megapixelsText(100, 100))
    }
}
