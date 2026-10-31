package xx.photobox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlideOrderTest {
    private val album = (1..10).map { "/a/$it.jpg" }

    /** Goes forward from [order] until the round is over, the photos shown on the way. */
    private fun walk(order: SlideOrder, repeat: Boolean = false): List<String> {
        val seen = mutableListOf(order.current!!)
        var at = order
        while (true) {
            at = at.next(repeat, album) ?: return seen
            seen += at.current!!
        }
    }

    @Test
    fun albumOrderGoesFromTheStartToTheLast() {
        assertEquals(album.drop(3), walk(albumOrder(album, 3)))
    }

    @Test
    fun albumOrderStartClampedIntoTheAlbum() {
        assertEquals(album.lastIndex, albumOrder(album, 99).pos)
        assertEquals(0, albumOrder(album, -1).pos)
        assertEquals(0, albumOrder(emptyList(), 5).pos)
        assertNull(albumOrder(emptyList(), 0).current)
    }

    @Test
    fun shuffleShowsTheWholeAlbumOnceFromThePhotoUp() {
        repeat(50) { seed ->
            val order = albumOrder(album, 6).withShuffle(true, album, Random(seed))
            val seen = walk(order)
            assertEquals(album[6], seen.first())
            assertEquals(album.toSet(), seen.toSet())
            assertEquals(album.size, seen.size)
        }
    }

    @Test
    fun shuffleOffGoesOnInAlbumOrderFromThePhotoUp() {
        val shuffled = albumOrder(album, 0).withShuffle(true, album, Random(1))
        val third = shuffled.next(false, album)!!.next(false, album)!!
        val back = third.withShuffle(false, album)
        assertFalse(back.shuffled)
        assertEquals(third.current, back.current)
        assertEquals(album.indexOf(third.current!!), back.pos)
    }

    @Test
    fun shuffleSwitchedToWhatItIsChangesNothing() {
        val order = albumOrder(album, 4)
        assertEquals(order, order.withShuffle(false, album))
        val shuffled = order.withShuffle(true, album, Random(2))
        assertEquals(shuffled, shuffled.withShuffle(true, album, Random(3)))
    }

    @Test
    fun repeatInAlbumOrderGoesRoundToTheFirst() {
        val last = albumOrder(album, album.lastIndex)
        assertNull(last.next(false, album))
        assertEquals(albumOrder(album, 0), last.next(true, album))
    }

    @Test
    fun repeatShuffledStartsANewRoundNotWithTheLastPhoto() {
        repeat(200) { seed ->
            val random = Random(seed)
            var order = albumOrder(album, 0).withShuffle(true, album, random)
            while (order.pos < order.paths.lastIndex) order = order.next(true, album, random)!!
            val last = order.current
            val round = order.next(true, album, random)!!
            assertTrue(round.shuffled)
            assertEquals(0, round.pos)
            assertNotEquals(last, round.current)
            assertEquals(album.toSet(), round.paths.toSet())
            assertEquals(album.size, round.paths.size)
        }
    }

    @Test
    fun aSinglePhotoRepeatsItself() {
        val one = listOf("/a/1.jpg")
        val shuffled = shuffledOrder(one, first = null, notFirst = one[0])
        assertEquals(one, shuffled.paths)
        assertEquals(one[0], shuffled.next(true, one)!!.current)
    }

    @Test
    fun previousStopsAtTheFirst() {
        assertEquals(0, albumOrder(album, 0).previous().pos)
        assertEquals(2, albumOrder(album, 3).previous().pos)
    }

    @Test
    fun startedOverFromTheFirstOrInANewRound() {
        assertEquals(albumOrder(album, 0), albumOrder(album, 7).startedOver(album))
        val shuffled = albumOrder(album, 7).withShuffle(true, album, Random(4))
        val again = shuffled.startedOver(album, Random(5))
        assertTrue(again.shuffled)
        assertEquals(0, again.pos)
        assertNotEquals(shuffled.current, again.current)
    }

    @Test
    fun reconciledAlbumOrderFollowsThePhotoUp() {
        val order = albumOrder(album, 5)
        val reloaded = listOf("/a/0.jpg") + album
        val after = order.reconciled(reloaded)
        assertEquals(reloaded, after.paths)
        assertEquals(album[5], after.current)
        // Unchanged, it is the same order, so nothing keyed on it starts over.
        assertEquals(order, order.reconciled(album.toList()))
    }

    @Test
    fun reconciledWithThePhotoUpGoneKeepsItsPlace() {
        val after = albumOrder(album, 5).reconciled(album - album[5])
        assertEquals(5, after.pos)
        assertEquals(album[6], after.current)
        val shorter = albumOrder(album, 9).reconciled(album.take(3))
        assertEquals(2, shorter.pos)
    }

    @Test
    fun reconciledShuffleKeepsItsOrderAndPutsNewPhotosAfter() {
        val order = albumOrder(album, 2).withShuffle(true, album, Random(6)).next(false, album)!!
        val gone = order.paths[5]
        val added = listOf("/a/new1.jpg", "/a/new2.jpg")
        val after = order.reconciled(album - gone + added, Random(7))
        assertTrue(after.shuffled)
        assertEquals(order.paths - gone, after.paths.take(album.size - 1))
        assertEquals(added.toSet(), after.paths.drop(album.size - 1).toSet())
        assertEquals(order.current, after.current)
    }

    @Test
    fun aPlaceIsKeptOnlyInAlbumOrderWhileTheShowRuns() {
        val running = albumOrder(album, 4)
        assertEquals(album[4], running.frameToKeep(ended = false))
        assertNull(running.frameToKeep(ended = true))
        assertNull(running.withShuffle(true, album, Random(9)).frameToKeep(ended = false))
    }
}
