package xx.photobox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageAccessTest {
    private val roots = listOf(File("/storage/emulated/0"), File("/storage/1A2B-3C4D"))

    @Test
    fun aFileOnAVolumeIsOnStorage() {
        assertTrue(isOnStorage(File("/storage/emulated/0/DCIM/x.jpg"), roots))
        assertTrue(isOnStorage(File("/storage/1A2B-3C4D/DCIM/x.jpg"), roots))
    }

    @Test
    fun theAppsOwnFilesAreNot() {
        assertFalse(isOnStorage(File("/data/user/0/xx.photobox/files/dates.tsv"), roots))
    }

    @Test
    fun climbingOutIsSeenThrough() {
        assertFalse(isOnStorage(File("/storage/emulated/0/../../../data/x"), roots))
    }

    @Test
    fun aNameThatOnlyStartsLikeAVolumeIsNot() {
        assertFalse(isOnStorage(File("/storage/emulated/0x/a.jpg"), roots))
        // The volume itself is a folder, not a file to open.
        assertFalse(isOnStorage(File("/storage/emulated/0"), roots))
    }
}
