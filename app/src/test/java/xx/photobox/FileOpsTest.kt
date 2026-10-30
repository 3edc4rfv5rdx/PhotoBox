package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOpsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, text: String = path, modified: Long = 1_000_000L): File =
        File(tmp.root, path).apply {
            parentFile!!.mkdirs()
            writeText(text)
            setLastModified(modified)
        }

    private fun dir(path: String): File = File(tmp.root, path).apply { mkdirs() }

    @Test
    fun numberGoesBeforeTheExtension() {
        assertEquals("a.jpg", numberedName("a.jpg", 0))
        assertEquals("a (2).jpg", numberedName("a.jpg", 2))
        assertEquals("a.b (1).jpg", numberedName("a.b.jpg", 1))
        assertEquals("noext (1)", numberedName("noext", 1))
        // A leading dot is a hidden name, not an extension.
        assertEquals(".hidden (1)", numberedName(".hidden", 1))
    }

    @Test
    fun moveRenamesAPhotoAndItsRawTogether() {
        val photo = file("A/p.jpg")
        val raw = file("A/p.dng")
        val to = dir("B")
        val moved = transferGroup(listOf(photo, raw), to, Transfer.MOVE)
        assertEquals(listOf(File(to, "p.jpg"), File(to, "p.dng")), moved)
        assertFalse(photo.exists())
        assertFalse(raw.exists())
        assertEquals("A/p.jpg", File(to, "p.jpg").readText())
    }

    @Test
    fun aTakenNameGetsOneNumberForTheWholeGroup() {
        val photo = file("A/p.jpg")
        val raw = file("A/p.dng")
        val to = dir("B")
        // Only the RAW's name is taken, yet the photo is numbered with it, so they stay a pair.
        val there = file("B/p.dng", text = "old")
        val moved = transferGroup(listOf(photo, raw), to, Transfer.MOVE)
        assertEquals(listOf(File(to, "p (1).jpg"), File(to, "p (1).dng")), moved)
        assertEquals("old", there.readText())
        assertFalse(File(to, "p.jpg").exists())
    }

    @Test
    fun copyKeepsTheOriginalAndItsTime() {
        val photo = file("A/p.jpg", modified = 1_234_000L)
        val to = dir("B")
        val copied = transferGroup(listOf(photo), to, Transfer.COPY)!!
        assertTrue(photo.exists())
        assertEquals("A/p.jpg", copied[0].readText())
        assertEquals(1_234_000L, copied[0].lastModified())
    }

    @Test
    fun copyIntoItsOwnFolderIsNumbered() {
        val photo = file("A/p.jpg")
        val copied = transferGroup(listOf(photo), photo.parentFile!!, Transfer.COPY)
        assertEquals(listOf(File(photo.parentFile, "p (1).jpg")), copied)
        assertTrue(photo.exists())
    }

    @Test
    fun aFailedCopyLeavesNoHalfOfTheGroup() {
        val photo = file("A/p.jpg")
        // Gone before it is copied: the photo's copy made first must not stay on its own.
        val raw = File(tmp.root, "A/p.dng")
        val to = dir("B")
        assertNull(transferGroup(listOf(photo, raw), to, Transfer.COPY))
        assertFalse(File(to, "p.jpg").exists())
        assertTrue(photo.exists())
    }

    @Test
    fun renameKeepsEachExtensionAndTakesTheRawAlong() {
        val photo = file("A/p.jpg")
        file("A/p.dng")
        val moves = renameTargets(photo, "trip")
        assertEquals(listOf("p.jpg" to "trip.jpg", "p.dng" to "trip.dng"), moves.map { (a, b) -> a.name to b.name })
    }

    @Test
    fun renameCheck() {
        val photo = file("A/p.jpg")
        file("A/taken.jpg")
        // Only the RAW of the name is there: the pair would still clash.
        file("A/rawonly.dng")
        file("A/p.dng")
        assertEquals(NameCheck.EMPTY, checkRename(photo, "  "))
        assertEquals(NameCheck.INVALID, checkRename(photo, "a/b"))
        assertEquals(NameCheck.UNCHANGED, checkRename(photo, " p "))
        assertEquals(NameCheck.EXISTS, checkRename(photo, "taken"))
        assertEquals(NameCheck.EXISTS, checkRename(photo, "rawonly"))
        assertEquals(NameCheck.OK, checkRename(photo, "trip"))
    }

    @Test
    fun intoAFolderThatIsNotThereNothingMoves() {
        val photo = file("A/p.jpg")
        assertNull(transferGroup(listOf(photo), File(tmp.root, "Missing"), Transfer.MOVE))
        assertTrue(photo.exists())
    }
}
