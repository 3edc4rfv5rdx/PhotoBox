package xx.photobox

import kotlin.random.Random

/**
 * The order a slideshow goes through its photos, by path, and where in it it is.
 * In album order [paths] is the album itself; [shuffled], it is one round of
 * the album with every photo once.
 */
data class SlideOrder(val paths: List<String>, val pos: Int, val shuffled: Boolean) {
    /** The photo up, or null with nothing to show. */
    val current: String? get() = paths.getOrNull(pos)
}

/** The album in its own order, at [at]. */
fun albumOrder(photos: List<String>, at: Int): SlideOrder =
    SlideOrder(photos, at.coerceIn(0, maxOf(photos.lastIndex, 0)), shuffled = false)

/** The album in its own order, at [path], or at its first photo when [path] is not in it. */
fun albumOrderAt(photos: List<String>, path: String?): SlideOrder =
    albumOrder(photos, if (path == null) 0 else photos.indexOf(path).coerceAtLeast(0))

/**
 * One round of [photos] in a random order: [first] first when given and there,
 * the rest after it each once; without one, anything but [notFirst] first, so a
 * new round does not show the last photo of the one before twice in a row.
 */
fun shuffledOrder(photos: List<String>, first: String?, notFirst: String? = null, random: Random = Random): SlideOrder {
    val paths = if (first != null && first in photos) {
        listOf(first) + photos.filter { it != first }.shuffled(random)
    } else {
        val round = photos.shuffled(random).toMutableList()
        if (round.size > 1 && round[0] == notFirst) {
            val other = random.nextInt(1, round.size)
            round[0] = round[other]
            round[other] = notFirst
        }
        round
    }
    return SlideOrder(paths, 0, shuffled = true)
}

/** Shuffled from the photo up, or back in album order at it. */
fun SlideOrder.withShuffle(on: Boolean, photos: List<String>, random: Random = Random): SlideOrder = when {
    on == shuffled -> this
    on -> shuffledOrder(photos, current, random = random)
    else -> albumOrderAt(photos, current)
}

/**
 * One photo on, or null past the last when not [repeat]ing. Repeating, album
 * order goes round to the first; a shuffled one starts a new round.
 */
fun SlideOrder.next(repeat: Boolean, photos: List<String>, random: Random = Random): SlideOrder? = when {
    pos < paths.lastIndex -> copy(pos = pos + 1)
    !repeat -> null
    shuffled -> shuffledOrder(photos, first = null, notFirst = current, random = random)
    else -> albumOrder(photos, 0)
}

/** One photo back; at the first it stays there. */
fun SlideOrder.previous(): SlideOrder = copy(pos = (pos - 1).coerceAtLeast(0))

/** From the start again: the album's first photo, or a new round. */
fun SlideOrder.startedOver(photos: List<String>, random: Random = Random): SlideOrder =
    if (shuffled) shuffledOrder(photos, first = null, notFirst = current, random = random) else albumOrder(photos, 0)

/**
 * Brought up to an album that has changed under the show. Album order is the
 * album again, at the photo up. A shuffled round keeps its order without the
 * photos gone, the new ones put after it in a random order. Where the photo up
 * is gone, the show stays at the place it had in the order, or the last one.
 */
fun SlideOrder.reconciled(photos: List<String>, random: Random = Random): SlideOrder {
    val at = current
    val paths = if (shuffled) {
        val present = photos.toHashSet()
        val kept = paths.filter { it in present }
        val known = kept.toHashSet()
        kept + photos.filter { it !in known }.shuffled(random)
    } else {
        photos
    }
    val found = if (at == null) -1 else paths.indexOf(at)
    return SlideOrder(paths, if (found >= 0) found else pos.coerceIn(0, maxOf(paths.lastIndex, 0)), shuffled)
}
