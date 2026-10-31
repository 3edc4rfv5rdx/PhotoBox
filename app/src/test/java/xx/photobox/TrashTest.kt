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

    private val trash get() = Trash.dirOn(tmp.root)

    private fun file(path: String, text: String = "x"): File =
        File(tmp.root, path).apply {
            parentFile!!.mkdirs()
            writeText(text)
        }

    private fun list() = Trash.list(listOf(trash))

    @Test
    fun aTrashedFileGoesIntoTheVolumesTrashAndComesBack() {
        val photo = file("DCIM/Camera/a.jpg", "photo")
        assertTrue(Trash.moveToTrash(photo, trash, now = 1000))
        assertFalse(photo.exists())
        assertTrue(File(trash, NOMEDIA_NAME).exists())
        assertTrue(File(trash, "1000.jpg").exists())

        val entry = list().single()
        assertEquals(photo, entry.original)
        assertEquals(1000L, entry.deletedAt)

        assertEquals(RestoreResult.OK, Trash.restore(entry))
        assertEquals("photo", photo.readText())
        assertTrue(list().isEmpty())
    }

    @Test
    fun aRawGoesAndComesBackWithItsPhoto() {
        val photo = file("A/IMG_1.jpg")
        val raw = file("A/IMG_1.DNG")
        file("A/IMG_2.dng")
        Trash.moveToTrash(photo, trash, now = 5)
        assertFalse(raw.exists())
        val entry = list().single()
        assertEquals(1, entry.companions.size)
        assertEquals(RestoreResult.OK, Trash.restore(entry))
        assertTrue(photo.exists() && raw.exists())
    }

    @Test
    fun restoreOntoATakenNameMovesNothing() {
        val photo = file("A/a.jpg", "old")
        Trash.moveToTrash(photo, trash, now = 1)
        file("A/a.jpg", "new")
        val entry = list().single()
        assertEquals(RestoreResult.EXISTS, Trash.restore(entry))
        assertTrue(entry.file.exists())
        assertEquals("new", photo.readText())
    }

    @Test
    fun restoreMakesAFolderDeletedMeanwhileAgain() {
        val photo = file("Gone/a.jpg")
        Trash.moveToTrash(photo, trash, now = 1)
        File(tmp.root, "Gone").delete()
        assertEquals(RestoreResult.OK, Trash.restore(list().single()))
        assertTrue(photo.exists())
    }

    @Test
    fun twoFilesAtTheSameMomentKeepBoth() {
        Trash.moveToTrash(file("A/a.jpg", "first"), trash, now = 7)
        Trash.moveToTrash(file("B/a.jpg", "second"), trash, now = 7)
        assertEquals(setOf("A", "B"), list().map { it.original.parentFile!!.name }.toSet())
    }

    @Test
    fun aStrayFileInTheTrashIsNeverWrittenOver() {
        file("$TRASH_PATH/7.jpg", "stray")
        Trash.moveToTrash(file("A/a.jpg"), trash, now = 7)
        assertEquals("stray", File(trash, "7.jpg").readText())
    }

    @Test
    fun onlyWhatIsOlderThanTheCutoffIsPurged() {
        Trash.moveToTrash(file("A/old.jpg"), trash, now = 100)
        Trash.moveToTrash(file("B/new.jpg"), trash, now = 900)
        assertEquals(1, Trash.purgeOlderThan(listOf(trash), cutoff = 500))
        assertEquals(listOf("new.jpg"), list().map { it.original.name })
    }

    @Test
    fun aRecordWhoseFileIsGoneIsDropped() {
        Trash.moveToTrash(file("A/a.jpg"), trash, now = 3)
        File(trash, "3.jpg").delete()
        File(trash, "3.path").setLastModified(System.currentTimeMillis() - TRASH_RECORD_GRACE_MS - 1000)
        assertTrue(list().isEmpty())
        assertFalse(File(trash, "3.path").exists())
    }

    @Test
    fun aListingBetweenTheRecordAndTheRenameLeavesTheEntryWhole() {
        val photo = file("A/a.jpg", "photo")
        assertTrue(Trash.moveToTrash(photo, trash, now = 4))
        // As it was a moment earlier: the record written, the file not renamed in yet.
        val stored = File(trash, "4.jpg")
        val waiting = File(tmp.root, "waiting.jpg")
        assertTrue(stored.renameTo(waiting))
        assertTrue(list().isEmpty())
        assertTrue(File(trash, "4.path").exists())
        assertTrue(waiting.renameTo(stored))
        val entry = list().single()
        assertEquals(photo, entry.original)
        assertEquals(RestoreResult.OK, Trash.restore(entry))
        assertEquals("photo", photo.readText())
    }

    @Test
    fun aFileWithNoRecordIsListedAndGoesBackToRestored() {
        file("$TRASH_PATH/.nomedia")
        file("$TRASH_PATH/42-c1.dng", "raw")
        val entry = list().single()
        assertEquals(File(trash, "42-c1.dng"), entry.file)
        assertEquals(42L, entry.deletedAt)
        val back = File(tmp.root, "$TRASH_ORPHAN_RESTORE_PATH/42-c1.dng")
        assertEquals(back, entry.original)
        assertEquals(RestoreResult.OK, Trash.restore(entry))
        assertEquals("raw", back.readText())
        assertTrue(list().isEmpty())
    }

    @Test
    fun aFileWithNoRecordIsPurgedWithItsTime() {
        file("$TRASH_PATH/42.jpg")
        Trash.moveToTrash(file("A/kept.jpg"), trash, now = 900)
        assertEquals(1, Trash.purgeOlderThan(listOf(trash), cutoff = 500))
        assertFalse(File(trash, "42.jpg").exists())
        assertEquals(listOf("kept.jpg"), list().map { it.original.name })
    }

    @Test
    fun theOldPerFolderTrashIsCarriedOverWithItsTimes() {
        file("A/.PhotoTrash/.nomedia")
        file("A/.PhotoTrash/100-IMG_1.jpg", "photo")
        file("A/.PhotoTrash/100-IMG_1.dng", "raw")
        file("B/.PhotoTrash/200-x.png")
        file("B/.PhotoTrash/stray.jpg")
        val moved = Trash.migrateLegacy(listOf(tmp.root), { trash }) {}
        assertEquals(2, moved)
        val entries = list()
        assertEquals(listOf(200L, 100L), entries.map { it.deletedAt })
        val photo = entries.last()
        assertEquals(File(tmp.root, "A/IMG_1.jpg"), photo.original)
        assertEquals(1, photo.companions.size)
        // Emptied, the old trash goes; one with a file it did not write stays.
        assertFalse(File(tmp.root, "A/.PhotoTrash").exists())
        assertTrue(File(tmp.root, "B/.PhotoTrash/stray.jpg").exists())
        assertEquals(RestoreResult.OK, Trash.restore(photo))
        assertEquals("raw", File(tmp.root, "A/IMG_1.dng").readText())
    }

    @Test
    fun oldNamesParseOnlyAsTheOldTrashWroteThem() {
        assertEquals(123L to "a-b.jpg", Trash.parseLegacyName("123-a-b.jpg"))
        assertNull(Trash.parseLegacyName("photo.jpg"))
        assertNull(Trash.parseLegacyName("12x-a.jpg"))
        assertNull(Trash.parseLegacyName("-a.jpg"))
        assertNull(Trash.parseLegacyName("123-"))
    }
}
