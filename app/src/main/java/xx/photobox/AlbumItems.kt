package xx.photobox

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One photo or video in the open album. [taken] is when it was taken, or its file time without a record of that. */
class MediaItem(val file: File, val taken: Long, val size: Long)

enum class ItemOrder { DATE, NAME, SIZE }

/** [items] newest, A to Z or largest first; ties fall back to the name, then the path. */
fun sortItems(items: List<MediaItem>, order: ItemOrder): List<MediaItem> {
    val byName = compareBy<MediaItem> { it.file.name.lowercase() }.thenBy { it.file.path }
    val comparator = when (order) {
        ItemOrder.DATE -> compareByDescending<MediaItem> { it.taken }.then(byName)
        ItemOrder.NAME -> byName
        ItemOrder.SIZE -> compareByDescending<MediaItem> { it.size }.then(byName)
    }
    return items.sortedWith(comparator)
}

/** A line of the thumbnail grid: a day's heading across the whole width, or one file. */
sealed class GridEntry {
    data class Day(val date: LocalDate) : GridEntry()
    class Item(val item: MediaItem) : GridEntry()
}

/**
 * [sorted] as grid lines, a [GridEntry.Day] heading before each day's files
 * when they are in date order; in any other order the days are scattered
 * and headings would only cut the grid up, so there are none.
 */
fun gridEntries(sorted: List<MediaItem>, order: ItemOrder, zone: ZoneId = ZoneId.systemDefault()): List<GridEntry> {
    if (order != ItemOrder.DATE) return sorted.map { GridEntry.Item(it) }
    val entries = ArrayList<GridEntry>(sorted.size + sorted.size / 8)
    var day: LocalDate? = null
    for (item in sorted) {
        val itemDay = dayOf(item.taken, zone)
        if (itemDay != day) {
            entries += GridEntry.Day(itemDay)
            day = itemDay
        }
        entries += GridEntry.Item(item)
    }
    return entries
}

fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
