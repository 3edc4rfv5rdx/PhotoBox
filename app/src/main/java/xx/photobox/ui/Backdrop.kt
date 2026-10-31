package xx.photobox.ui

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** The long side of a slide's backdrop: so few pixels that only the photo's colours are left of it. */
internal const val BACKDROP_SIDE = 12

/**
 * The sizes a [width]×[height] photo goes down through to a long side of
 * [side]: halved while more than twice that, each pixel of a halving the mean
 * of four, so no part of the photo is skipped over; then once to [side]. Empty
 * for a photo no bigger than that already.
 */
internal fun backdropSteps(width: Int, height: Int, side: Int = BACKDROP_SIDE): List<Pair<Int, Int>> {
    val steps = mutableListOf<Pair<Int, Int>>()
    var w = width
    var h = height
    while (maxOf(w, h) > side * 2) {
        w = maxOf(1, w / 2)
        h = maxOf(1, h / 2)
        steps += w to h
    }
    val scale = side.toFloat() / maxOf(w, h)
    if (scale < 1f) steps += maxOf(1, (w * scale).roundToInt()) to maxOf(1, (h * scale).roundToInt())
    return steps
}

/** [photo] brought down to a few pixels of its colours, to be stretched behind it as a blur. Off the main thread: it reads the whole photo. */
internal fun backdropOf(photo: Bitmap): Bitmap {
    var current = photo
    for ((w, h) in backdropSteps(photo.width, photo.height)) {
        val next = current.scale(w, h)
        // Scaled to its own size it can come back as itself.
        if (current !== photo && next !== current) current.recycle()
        current = next
    }
    return current
}
