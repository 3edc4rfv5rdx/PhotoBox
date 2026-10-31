package xx.photobox.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.abs

// Compose scrolls a list or a pager for a mouse by its wheel only: a drag with
// the button held is left to the touch screen. A TV box's air mouse has no
// wheel, so without these nothing on the screen moves under it. A finger never
// gets here — these only take a drag that starts with a mouse button.

/** How far, as a part of a page, a mouse has to pull the viewer for it to turn the page. */
internal const val MOUSE_PAGE_TURN = 0.15f

/** The page a mouse pull from [startPage] settles on, the pager now at [position] in pages: the next or the one before once pulled past [MOUSE_PAGE_TURN], else back where it was. */
internal fun mousePageTarget(startPage: Int, position: Float, pageCount: Int): Int {
    val pulled = position - startPage
    val step = when {
        pulled > MOUSE_PAGE_TURN -> 1
        pulled < -MOUSE_PAGE_TURN -> -1
        else -> 0
    }
    return (startPage + step).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

/** The scroll a pointer moved [drag] pixels along the axis asks for: what is under it goes with it, so the other way, unless the axis runs backward. */
internal fun mouseScrollDelta(drag: Float, reverse: Boolean): Float = if (reverse) drag else -drag

/** Whether scrolling runs against the pointer's axis: a reversed layout, or across the screen in a right-to-left language. */
@Composable
private fun reversed(vertical: Boolean, reverseLayout: Boolean): Boolean =
    reverseLayout != (!vertical && LocalLayoutDirection.current == LayoutDirection.Rtl)

/** [state]'s list or grid pulled along with a mouse's button held, and flung on when let go. */
@Composable
fun Modifier.mouseDragScroll(state: ScrollableState, vertical: Boolean = true, reverseLayout: Boolean = false): Modifier {
    val reverse = reversed(vertical, reverseLayout)
    val fling = ScrollableDefaults.flingBehavior()
    val scope = rememberCoroutineScope()
    return pointerInput(state, vertical, reverse, fling) {
        mouseDrags(
            vertical = vertical,
            reverse = reverse,
            enabled = { true },
            onStart = {},
            onDrag = { state.dispatchRawDelta(it) },
            onEnd = { velocity -> scope.launch { state.scroll { with(fling) { performFling(velocity) } } } },
        )
    }
}

/** A column that scrolls, by a finger or a mouse alike. */
@Composable
fun Modifier.verticalScrollByAny(state: ScrollState = rememberScrollState()): Modifier =
    mouseDragScroll(state).verticalScroll(state)

/** [state]'s pages pulled along with a mouse's button held, turned one on or back when let go — as a swipe would. Not while [enabled] is false. */
@Composable
fun Modifier.mouseDragPages(state: PagerState, vertical: Boolean, reverseLayout: Boolean, enabled: Boolean): Modifier {
    val reverse = reversed(vertical, reverseLayout)
    val on by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    return pointerInput(state, vertical, reverse) {
        var startPage = 0
        mouseDrags(
            vertical = vertical,
            reverse = reverse,
            enabled = { on },
            onStart = { startPage = state.currentPage },
            onDrag = { state.dispatchRawDelta(it) },
            onEnd = {
                val target = mousePageTarget(startPage, state.currentPage + state.currentPageOffsetFraction, state.pageCount)
                scope.launch { state.animateScrollToPage(target) }
            },
        )
    }
}

/**
 * Every drag a mouse makes with its button held, along one axis, as scroll
 * deltas. Taken ahead of what lies under it, once past the touch slop, so a
 * cell dragged across is not clicked or long-pressed too; a click that does not
 * move is left alone.
 */
private suspend fun PointerInputScope.mouseDrags(
    vertical: Boolean,
    reverse: Boolean,
    enabled: () -> Boolean,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (velocity: Float) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (down.type != PointerType.Mouse || !enabled()) return@awaitEachGesture
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    var pulled = 0f
    var dragging = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) {
            // Let go after a drag: not a click on whatever is under it now.
            if (dragging) change.consume()
            break
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        val moved = change.position - change.previousPosition
        val along = if (vertical) moved.y else moved.x
        if (dragging) {
            onDrag(mouseScrollDelta(along, reverse))
        } else {
            pulled += along
            if (abs(pulled) < viewConfiguration.touchSlop) continue
            dragging = true
            onStart()
            onDrag(mouseScrollDelta(pulled, reverse))
        }
        change.consume()
    }
    if (dragging) {
        val velocity = tracker.calculateVelocity()
        onEnd(mouseScrollDelta(if (vertical) velocity.y else velocity.x, reverse))
    }
}
