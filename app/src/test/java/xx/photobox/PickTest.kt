package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickTest {
    @Test
    fun wildcardsAndExactTypes() {
        assertTrue(mimeMatches("*/*", "video/mp4"))
        assertTrue(mimeMatches("image/*", "image/jpeg"))
        assertFalse(mimeMatches("image/*", "video/mp4"))
        assertTrue(mimeMatches("image/jpeg", "image/jpeg"))
        assertFalse(mimeMatches("image/jpeg", "image/png"))
        // A family alone is taken as the whole family.
        assertTrue(mimeMatches("image", "image/png"))
    }

    @Test
    fun mediaLibraryTypesReadAsFamilies() {
        assertEquals("image/*", pickedType("vnd.android.cursor.dir/image"))
        assertEquals("video/*", pickedType("vnd.android.cursor.dir/video"))
        assertEquals("image/png", pickedType("IMAGE/PNG"))
    }
}
