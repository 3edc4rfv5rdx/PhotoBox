package xx.photobox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesBusyTest {
    @Test
    fun aMoveRunningKeepsTheTrashFromChanging() {
        // What a restore asks: a move or copy job alone must already say no.
        assertTrue(filesBusy(trashBusy = false, fileJob = FileJob.MOVE, changing = false))
        assertTrue(filesBusy(trashBusy = false, fileJob = FileJob.COPY, changing = false))
    }

    @Test
    fun aRenameOrTheTrashChangingIsBusyToo() {
        assertTrue(filesBusy(trashBusy = false, fileJob = null, changing = true))
        assertTrue(filesBusy(trashBusy = true, fileJob = null, changing = false))
    }

    @Test
    fun nothingRunningIsNotBusy() {
        assertFalse(filesBusy(trashBusy = false, fileJob = null, changing = false))
    }
}
