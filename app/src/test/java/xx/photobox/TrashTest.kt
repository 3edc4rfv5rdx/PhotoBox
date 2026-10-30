package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, text: String = "x"): File =
        File(tmp.root, path).apply {
            parentFile!!.mkdirs()
            writeText(text)
        }

    private fun list() = Trash.list(listOf(tmp.root)) {}

    @Test
    fun trashNameKeepsTheTimeAndTheWholeName() {
        assertEquals("123-a-b.jpg", Trash.trashName("a-b.jpg", 123))
        assertEquals(123L to "a-b.jpg", Trash.parseTrashName("123-a-b.jpg"))
        assertNull(Trash.parseTrashName("photo.jpg"))
        assertNull(Trash.parseTrashName("12x-a.jpg"))
        assertNull(Trash.parseTrashName("-a.jpg"))
        assertNull(Trash.parseTrashName("123-"))
    }

    @Test
    fun aTrashedFileGoesIntoItsFoldersHiddenTrashAndComesBack() {
        val photo = file("DCIM/Camera/a.jpg", "photo")
        assertTrue(Trash.moveToTrash(photo, now = 1000))
        assertFalse(photo.exists())
        val dir = File(tmp.root, "DCIM/Camera/$TRASH_DIR_NAME")
        assertTrue(File(dir, NOMEDIA_NAME).exists())
        assertTrue(File(dir, "1000-a.jpg").exists())

        val entry = list().single()
        assertEquals(photo, entry.original)
        assertEquals(1000L, entry.deletedAt)

        assertEquals(RestoreResult.OK, Trash.restore(entry))
        assertEquals("photo", photo.readText())
        // Nothing but the .nomedia left: the trash folder goes too.
        assertFalse(dir.exists())
    }

    @Test
    fun aRawGoesAndComesBackWithItsPhoto() {
        val photo = file("A/IMG_1.jpg")
        val raw = file("A/IMG_1.DNG")
        file("A/IMG_2.dng")
        Trash.moveToTrash(photo, now = 5)
        assertFalse(raw.exists())
        val entry = list().single()
        assertEquals(1, entry.companions.size)
        assertEquals(RestoreResult.OK, Trash.restore(entry))
        assertTrue(photo.exists() && raw.exists())
    }

    @Test
    fun restoreOntoATakenNameMovesNothing() {
        val photo = file("A/a.jpg", "old")
        Trash.moveToTrash(photo, now = 1)
        file("A/a.jpg", "new")
        val entry = list().single()
        assertEquals(RestoreResult.EXISTS, Trash.restore(entry))
        assertTrue(entry.file.exists())
        assertEquals("new", photo.readText())
    }

    @Test
    fun theSameNameTrashedTwiceKeepsBoth() {
        Trash.moveToTrash(file("A/a.jpg", "first"), now = 1)
        Trash.moveToTrash(file("A/a.jpg", "second"), now = 2)
        val entries = list()
        assertEquals(listOf(2L, 1L), entries.map { it.deletedAt })
    }

    @Test
    fun onlyWhatIsOlderThanTheCutoffIsPurged() {
        Trash.moveToTrash(file("A/old.jpg"), now = 100)
        Trash.moveToTrash(file("B/new.jpg"), now = 900)
        assertEquals(1, Trash.purgeOlderThan(listOf(tmp.root), cutoff = 500) {})
        assertEquals(listOf("new.jpg"), list().map { it.original.name })
        assertFalse(File(tmp.root, "A/$TRASH_DIR_NAME").exists())
    }

    @Test
    fun filesTheTrashDidNotNameAreLeftAlone() {
        file("A/$TRASH_DIR_NAME/stray.jpg")
        assertTrue(list().isEmpty())
    }
}
