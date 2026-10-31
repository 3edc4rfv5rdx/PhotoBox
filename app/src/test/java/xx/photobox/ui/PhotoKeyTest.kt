package xx.photobox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhotoKeyTest {
    private val path = "/storage/emulated/0/DCIM/IMG_1.jpg"

    @Test
    fun anotherFileAtTheSamePathGetsAnotherKey() {
        assertNotEquals(cacheKey(0, path, 100, 5), cacheKey(0, path, 200, 5))
        assertNotEquals(cacheKey(0, path, 100, 5), cacheKey(0, path, 100, 6))
    }

    @Test
    fun aTurnInPlaceGetsAnotherKeyWithTheSameStamp() {
        assertNotEquals(cacheKey(0, path, 100, 5), cacheKey(1, path, 100, 5))
    }

    @Test
    fun theSameFileKeepsItsKey() {
        assertEquals(cacheKey(2, path, 100, 5), cacheKey(2, path, 100, 5))
    }

    @Test
    fun partsCannotRunIntoEachOther() {
        // A path is the last part, so digits of the stamp never read as part of it.
        assertNotEquals(cacheKey(1, "2:3:/a", 4, 5), cacheKey(1, "/a", 2, 3))
        assertNotEquals(cacheKey(11, path, 1, 5), cacheKey(1, path, 11, 5))
    }
}
