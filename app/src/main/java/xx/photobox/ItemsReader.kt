package xx.photobox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** EXIF reads run a few at a time: enough to overlap the waits on storage, not so many they fight over it. */
private val dateReader = Dispatchers.IO.limitedParallelism(4)

private var sharedCache: DateCache? = null

/**
 * The one [DateCache] of the process: the gallery and a file opened from
 * another app read and write the same file, which two instances would each
 * overwrite with their own.
 */
@Synchronized
fun dateCache(context: Context): DateCache =
    sharedCache ?: DateCache(File(context.applicationContext.filesDir, "dates.tsv")).also { sharedCache = it }

/**
 * [dir]'s photos and videos, each with when it was taken. Dates come from
 * [cache] where it has them; the rest are read a few at a time and written
 * back. [onToRead] hears, on the caller's thread, how many must be read, and
 * [done] counts them as they are, for progress.
 */
suspend fun readItems(dir: File, cache: DateCache, done: AtomicInteger, onToRead: (Int) -> Unit): List<MediaItem> {
    val known = ArrayList<MediaItem>()
    val unknown = ArrayList<Triple<File, Long, Long>>()
    withContext(Dispatchers.IO) {
        for (file in dir.listFiles().orEmpty()) {
            if (!file.isFile || !isMedia(file)) continue
            val modified = file.lastModified()
            val size = file.length()
            val taken = cache.get(file, modified, size)
            if (taken != null) known += MediaItem(file, taken, size) else unknown += Triple(file, modified, size)
        }
    }
    onToRead(unknown.size)
    val read = withContext(dateReader) {
        coroutineScope {
            unknown.map { (file, modified, size) ->
                async {
                    val taken = takenAt(file)
                    cache.put(file, modified, size, taken)
                    done.incrementAndGet()
                    MediaItem(file, taken, size)
                }
            }.awaitAll()
        }
    }
    if (read.isNotEmpty()) withContext(Dispatchers.IO) { cache.save() }
    return known + read
}
