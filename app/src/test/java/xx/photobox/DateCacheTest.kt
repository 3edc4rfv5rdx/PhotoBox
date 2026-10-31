package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DateCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store get() = File(tmp.root, "dates.tsv")
    private val photo = File("/a/p.jpg")
    private val other = File("/a/q.jpg")

    @Test
    fun aFailedSaveLeavesTheOldStoreAndKeepsTheEntries() {
        DateCache(store).apply {
            put(photo, 1, 2, 3)
            save()
        }
        val before = store.readText()
        val cache = DateCache(store)
        cache.put(other, 4, 5, 6)
        // The temp name taken by a folder: the write cannot even start, as with no space left.
        val blocker = File(store.path + ".tmp").apply { mkdir() }
        cache.save()
        assertEquals(before, store.readText())
        assertEquals(6L, cache.get(other, 4, 5))
        assertEquals(3L, cache.get(photo, 1, 2))
        // Still to be saved: once there is room, the next save writes it.
        blocker.delete()
        cache.save()
        assertEquals(6L, DateCache(store).get(other, 4, 5))
        assertFalse(File(store.path + ".tmp").exists())
    }

    @Test
    fun anUnreadableStoreStartsEmpty() {
        store.writeText("/a/p.jpg\t1\t2\t3\n")
        assumeFalse("root reads anything", System.getProperty("user.name") == "root")
        assertTrue(store.setReadable(false))
        val cache = DateCache(store)
        assertNull(cache.get(photo, 1, 2))
        cache.put(other, 4, 5, 6)
        assertEquals(6L, cache.get(other, 4, 5))
    }

    @Test
    fun aNameWithALineBreakIsNeverStored() {
        val cache = DateCache(store)
        cache.put(File("/a/x\ry.jpg"), 1, 2, 3)
        cache.put(photo, 1, 2, 3)
        cache.save()
        assertEquals(1, store.readLines().size)
        assertNull(DateCache(store).get(File("/a/x\ry.jpg"), 1, 2))
    }
}
