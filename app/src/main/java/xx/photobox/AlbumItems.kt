package xx.photobox

import java.io.File
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One photo or video in the open album. [taken] is when it was taken, or its
 * file time without a record of that; [size] and [modified] are the file's as
 * listed, what tells it from another file put at the same path later.
 */
class MediaItem(val file: File, val taken: Long, val size: Long, val modified: Long)

enum class ItemOrder { DATE, NAME, SIZE }

/**
 * What the grid is narrowed to: names that contain [name], case aside, taken
 * from [from] to [to], both days whole. A part left empty lets everything
 * through; dates given the wrong way round are taken the right way.
 */
data class ItemFilter(val name: String = "", val from: LocalDate? = null, val to: LocalDate? = null) : Serializable {
    val isEmpty: Boolean
        get() = name.isBlank() && from == null && to == null
}

/** Those of [items] that [filter] lets through, in the order given. */
fun filterItems(items: List<MediaItem>, filter: ItemFilter, zone: ZoneId = ZoneId.systemDefault()): List<MediaItem> {
    if (filter.isEmpty) return items
    val name = filter.name.trim()
    val (first, last) = if (filter.from != null && filter.to != null && filter.from > filter.to) {
        filter.to to filter.from
    } else {
        filter.from to filter.to
    }
    return items.filter { item ->
        val day = dayOf(item.taken, zone)
        (name.isEmpty() || item.file.name.contains(name, ignoreCase = true)) &&
            (first == null || day >= first) && (last == null || day <= last)
    }
}

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
