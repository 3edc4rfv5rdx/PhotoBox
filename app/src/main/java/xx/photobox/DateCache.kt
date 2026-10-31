package xx.photobox

import java.io.File
import java.io.IOException

/**
 * When each file was taken, kept on disk so an album opened again does not
 * read every file's EXIF a second time. An entry holds only while the file's
 * time and size are what they were when it was read: a file turned, edited or
 * replaced under the same name is read afresh.
 *
 * One line per file: path, time, size and date taken, tab-separated. The path
 * is read up to the last three tabs, so a tab in a name does not break it;
 * a name with a line break ([breaksLines]) is never stored.
 */
class DateCache(private val store: File) {
    private class Entry(val modified: Long, val size: Long, val taken: Long)

    private val entries = HashMap<String, Entry>()
    private var loaded = false
    private var dirty = false

    /** [file]'s date taken as last read, or null when it was never read or has changed since. */
    @Synchronized
    fun get(file: File, modified: Long, size: Long): Long? {
        load()
        val entry = entries[file.path] ?: return null
        return entry.taken.takeIf { entry.modified == modified && entry.size == size }
    }

    @Synchronized
    fun put(file: File, modified: Long, size: Long, taken: Long) {
        if (breaksLines(file.path)) return
        load()
        entries[file.path] = Entry(modified, size, taken)
        dirty = true
    }

    /**
     * Writes the entries out when any changed; a new file first, so a crash
     * mid-write leaves the old one whole. A write that fails — the phone's
     * storage full — leaves the old file as it was and is only a cache not
     * saved: the entries stay in memory, and the next save tries again.
     */
    @Synchronized
    fun save() {
        if (!dirty) return
        val temp = File(store.path + ".tmp")
        val saved = try {
            temp.bufferedWriter().use { out ->
                for ((path, e) in entries) out.write("$path\t${e.modified}\t${e.size}\t${e.taken}\n")
            }
            temp.renameTo(store)
        } catch (e: IOException) {
            false
        }
        if (saved) dirty = false else temp.delete()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (!store.isFile) return
        try {
            store.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@forEachLine
                val numbers = parts.takeLast(3).map { it.toLongOrNull() ?: return@forEachLine }
                entries[parts.dropLast(3).joinToString("\t")] = Entry(numbers[0], numbers[1], numbers[2])
            }
        } catch (e: IOException) {
            // Unreadable: every date is read again, as on a first start.
            entries.clear()
        }
    }
}
