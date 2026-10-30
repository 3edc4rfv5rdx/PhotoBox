package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlbumsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, modified: Long = 1_000L): File =
        File(tmp.root, path).apply {
            parentFile!!.mkdirs()
            writeText("x")
            setLastModified(modified)
        }

    private fun scan(): Map<String, Album> =
        AlbumScanner.scan(listOf(tmp.root)) {}.associateBy { it.dir.relativeTo(tmp.root).invariantSeparatorsPath }

    @Test
    fun folderWithMediaIsAnAlbumCountingOnlyItsOwnFiles() {
        file("DCIM/Camera/a.jpg")
        file("DCIM/Camera/b.MP4")
        file("DCIM/Camera/notes.txt")
        file("DCIM/Camera/Sub/c.png")
        val albums = scan()
        assertEquals(setOf("DCIM/Camera", "DCIM/Camera/Sub"), albums.keys)
        assertEquals(2, albums.getValue("DCIM/Camera").count)
        assertEquals(1, albums.getValue("DCIM/Camera/Sub").count)
    }

    @Test
    fun folderWithoutMediaIsNoAlbum() {
        file("Documents/a.pdf")
        File(tmp.root, "Empty").mkdirs()
        assertTrue(scan().isEmpty())
    }

    @Test
    fun coverIsTheNewestFile() {
        file("A/old.jpg", modified = 1_000L)
        file("A/new.jpg", modified = 5_000L)
        file("A/mid.jpg", modified = 3_000L)
        val album = scan().getValue("A")
        assertEquals("new.jpg", album.cover.name)
        assertEquals(5_000L, album.modified)
    }

    @Test
    fun dotFolderNomediaAndEverythingBelowThemAreHidden() {
        file("Visible/a.jpg")
        file(".Hidden/a.jpg")
        file(".Hidden/Inner/a.jpg")
        file("Muted/a.jpg")
        file("Muted/.NOMEDIA")
        file("Muted/Inner/a.jpg")
        val albums = scan()
        assertFalse(albums.getValue("Visible").hidden)
        assertTrue(albums.getValue(".Hidden").hidden)
        assertTrue(albums.getValue(".Hidden/Inner").hidden)
        assertTrue(albums.getValue("Muted").hidden)
        assertTrue(albums.getValue("Muted/Inner").hidden)
    }

    @Test
    fun trashIsNeverAnAlbum() {
        file("A/a.jpg")
        file("$TRASH_PATH/1-b.jpg")
        file("B/.PhotoTrash/1-c.jpg")
        assertEquals(setOf("A"), scan().keys)
    }

    @Test
    fun cancelStopsTheWalk() {
        file("A/a.jpg")
        val result = runCatching { AlbumScanner.scan(listOf(tmp.root)) { throw IllegalStateException() } }
        assertTrue(result.isFailure)
    }

    private fun album(path: String, count: Int, modified: Long) =
        Album(File("/r/$path"), File("/r"), count, File("/r/$path/x.jpg"), modified, hidden = false)

    @Test
    fun sortOrdersWithPathAsTieBreak() {
        val a = album("x/beta", count = 5, modified = 10)
        val b = album("y/Alpha", count = 5, modified = 30)
        val c = album("z/alpha", count = 9, modified = 20)
        assertEquals(listOf(b, c, a), sortAlbums(listOf(a, b, c), AlbumOrder.NAME))
        assertEquals(listOf(b, c, a), sortAlbums(listOf(a, b, c), AlbumOrder.DATE))
        assertEquals(listOf(c, a, b), sortAlbums(listOf(a, b, c), AlbumOrder.COUNT))
    }

    @Test
    fun albumNameCheck() {
        File(tmp.root, "Taken").mkdirs()
        assertEquals(AlbumNameCheck.EMPTY, checkAlbumName(tmp.root, "   "))
        assertEquals(AlbumNameCheck.INVALID, checkAlbumName(tmp.root, "a/b"))
        assertEquals(AlbumNameCheck.INVALID, checkAlbumName(tmp.root, ".."))
        assertEquals(AlbumNameCheck.EXISTS, checkAlbumName(tmp.root, " Taken "))
        assertEquals(AlbumNameCheck.OK, checkAlbumName(tmp.root, "Trips"))
    }

    @Test
    fun aFolderWithMusicIsNoAlbumButItsSubfoldersStillAre() {
        file("Music/Band/cover.jpg")
        file("Music/Band/01.mp3")
        file("Music/Band/Scans/booklet.jpg")
        assertEquals(setOf("Music/Band/Scans"), scan().keys)
    }

    @Test
    fun albumsBelongToTheRootTheyWereFoundUnder() {
        file("DCIM/Camera/a.jpg")
        file("Pictures/b.jpg")
        val roots = listOf(File(tmp.root, "DCIM"), File(tmp.root, "Pictures"))
        val byDir = AlbumScanner.scan(roots) {}.associate { it.dir.name to it.root.name }
        assertEquals(mapOf("Camera" to "DCIM", "Pictures" to "Pictures"), byDir)
    }

    @Test
    fun rootsInsideAnotherRootAreDropped() {
        assertEquals(
            listOf("/s/DCIM", "/s/Pictures"),
            normalizeRoots(listOf("/s/DCIM", "/s/DCIM/Camera", "/s/Pictures/", "/s/DCIM", "")),
        )
        // A sibling that only shares the start of the name is not inside.
        assertEquals(listOf("/s/Pic", "/s/Pictures"), normalizeRoots(listOf("/s/Pic", "/s/Pictures")))
        // A parent added after its child takes its place.
        assertEquals(listOf("/s"), normalizeRoots(listOf("/s/DCIM", "/s")))
    }
}
