package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AlbumItemsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun item(name: String, taken: Long = 0, size: Long = 0) = MediaItem(File("/a/$name"), taken, size)

    @Test
    fun sortsNewestByNameOrLargestWithNameAsTieBreak() {
        val a = item("a.jpg", taken = 10, size = 5)
        val b = item("B.jpg", taken = 30, size = 5)
        val c = item("c.jpg", taken = 30, size = 9)
        assertEquals(listOf(b, c, a), sortItems(listOf(a, b, c), ItemOrder.DATE))
        assertEquals(listOf(a, b, c), sortItems(listOf(c, b, a), ItemOrder.NAME))
        assertEquals(listOf(c, a, b), sortItems(listOf(a, b, c), ItemOrder.SIZE))
    }

    @Test
    fun dateOrderGetsAHeadingBeforeEachDay() {
        val sorted = listOf(item("1", at("2026-10-30", 18)), item("2", at("2026-10-30", 9)), item("3", at("2026-10-28")))
        val entries = gridEntries(sorted, ItemOrder.DATE, utc)
        assertEquals(5, entries.size)
        assertEquals(GridEntry.Day(LocalDate.parse("2026-10-30")), entries[0])
        assertTrue(entries[1] is GridEntry.Item && entries[2] is GridEntry.Item)
        assertEquals(GridEntry.Day(LocalDate.parse("2026-10-28")), entries[3])
    }

    @Test
    fun otherOrdersGetNoHeadings() {
        val sorted = listOf(item("1", at("2026-10-30")), item("2", at("2026-10-28")))
        assertTrue(gridEntries(sorted, ItemOrder.NAME, utc).all { it is GridEntry.Item })
    }

    @Test
    fun cacheHoldsOnlyWhileTheFileIsUnchanged() {
        val store = File(tmp.root, "dates.tsv")
        val file = File("/a/photo.jpg")
        DateCache(store).apply {
            put(file, modified = 100, size = 7, taken = 42)
            save()
        }
        val reread = DateCache(store)
        assertEquals(42L, reread.get(file, modified = 100, size = 7))
        assertNull(reread.get(file, modified = 101, size = 7))
        assertNull(reread.get(file, modified = 100, size = 8))
        assertNull(reread.get(File("/a/other.jpg"), modified = 100, size = 7))
    }

    @Test
    fun cacheKeepsATabInAName() {
        val store = File(tmp.root, "dates.tsv")
        val file = File("/a/odd\tname.jpg")
        DateCache(store).apply {
            put(file, modified = 1, size = 2, taken = 3)
            save()
        }
        assertEquals(3L, DateCache(store).get(file, modified = 1, size = 2))
    }

    @Test
    fun cacheSkipsBrokenLines() {
        val store = File(tmp.root, "dates.tsv").apply { writeText("garbage\n/a/x.jpg\t1\tnope\t3\n/a/y.jpg\t1\t2\t3\n") }
        val cache = DateCache(store)
        assertNull(cache.get(File("/a/x.jpg"), modified = 1, size = 2))
        assertEquals(3L, cache.get(File("/a/y.jpg"), modified = 1, size = 2))
    }

    @Test
    fun sameItemsIgnoresOrderButNotDatesSizesOrFiles() {
        val a = item("a.jpg", taken = 1, size = 2)
        val b = item("b.jpg", taken = 3, size = 4)
        assertTrue(sameItems(listOf(a, b), listOf(item("b.jpg", 3, 4), item("a.jpg", 1, 2))))
        assertTrue(!sameItems(null, listOf(a)))
        assertTrue(!sameItems(listOf(a, b), listOf(a)))
        assertTrue(!sameItems(listOf(a, b), listOf(a, item("b.jpg", 3, 5))))
        assertTrue(!sameItems(listOf(a, b), listOf(a, item("c.jpg", 3, 4))))
    }
}
