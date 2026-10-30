package xx.photobox.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xx.photobox.GRID_COLUMNS
import xx.photobox.GridEntry
import xx.photobox.ItemOrder
import xx.photobox.MediaItem
import xx.photobox.R
import xx.photobox.gridEntries
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val CELL_GAP = 2.dp
private val CELL_CORNER = 2.dp
private val SELECTED_BORDER = 3.dp
private val SELECTED_ICON = 24.dp
private const val SELECTED_SCRIM_ALPHA = 0.3f

/** How far fingers must spread or close, as a ratio of their distance, before the grid gains or loses a column. */
private const val PINCH_STEP = 1.25f

/** The fast scroller only shows on a grid this many screens long or more: a short one scrolls fine by hand. */
private const val FAST_SCROLL_MIN_SCREENS = 3

private val SCROLLER_WIDTH = 28.dp
private val SCROLLER_THUMB_HEIGHT = 48.dp
private val SCROLLER_THUMB_WIDTH = 6.dp

/**
 * The open album as a grid of [columns] thumbnails, [sorted] in [order], with a day
 * heading over each day's files in date order. A pinch changes the column
 * count through [onColumns]; one finger scrolls, and a long grid gets a bar on
 * the right edge to drag through it, labelled with where the drag has got to.
 * Empty, it says so — or, when [filtered], that the filter lets nothing through.
 * A tap opens a file through [onOpen]; a long tap picks it through
 * [onSelect], and while anything is [selected] a tap picks or drops too.
 */
@Composable
fun ThumbnailsScreen(
    sorted: List<MediaItem>?,
    datesDone: Int,
    datesTotal: Int,
    order: ItemOrder,
    columns: Int,
    onColumns: (Int) -> Unit,
    filtered: Boolean,
    selected: Set<String>,
    onOpen: (MediaItem) -> Unit,
    onSelect: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sorted == null) {
        if (datesTotal > 0) {
            ProgressState(
                text = stringResource(R.string.reading_dates, formatCount(datesDone), formatCount(datesTotal)),
                progress = { datesDone.toFloat() / datesTotal },
            )
        } else {
            ProgressState(text = null)
        }
        return
    }
    if (sorted.isEmpty()) {
        EmptyState(icon = null, title = stringResource(if (filtered) R.string.filter_nothing else R.string.album_empty))
        return
    }

    val context = LocalContext.current
    val entries = remember(sorted, order) { gridEntries(sorted, order) }
    val gridState = rememberLazyGridState()

    val currentColumns by rememberUpdatedState(columns)
    val currentOnColumns by rememberUpdatedState(onColumns)
    // Spreading the fingers brings the photos closer: fewer, larger ones.
    val pinch = Modifier.pointerInput(Unit) {
        detectPinchSteps { step ->
            val next = (currentColumns - step).coerceIn(GRID_COLUMNS)
            if (next != currentColumns) currentOnColumns(next)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().then(pinch)) {
        // Decoded at the cell's own size, in two steps so a pinch does not
        // throw away every thumbnail already decoded for a size close by.
        val cellPixels = with(LocalDensity.current) { (maxWidth / columns).toPx() }
        val decodeSize = if (cellPixels > THUMBNAIL_MAX_DIMENSION * 1.5f) THUMBNAIL_MAX_DIMENSION * 2 else THUMBNAIL_MAX_DIMENSION

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(bottom = CELL_GAP),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                entries,
                key = { entry ->
                    when (entry) {
                        is GridEntry.Day -> "day:${entry.date}"
                        is GridEntry.Item -> entry.item.file.path
                    }
                },
                span = { entry -> GridItemSpan(if (entry is GridEntry.Day) maxLineSpan else 1) },
                contentType = { it::class },
            ) { entry ->
                when (entry) {
                    is GridEntry.Day -> Text(
                        dayLabel(context, entry.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
                    )

                    is GridEntry.Item -> Box(
                        Modifier
                            .padding(CELL_GAP / 2)
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = { if (selected.isEmpty()) onOpen(entry.item) else onSelect(entry.item) },
                                onLongClick = { onSelect(entry.item) },
                            ),
                    ) {
                        PhotoThumbnail(
                            path = entry.item.file.path,
                            maxDimension = decodeSize,
                            corner = CELL_CORNER,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (entry.item.file.path in selected) SelectedMark()
                    }
                }
            }
        }

        FastScroller(state = gridState, label = { index -> scrollLabel(context, entries, index, order) })
    }
}

/** Over a picked cell: an accent tint and frame, and a tick in the corner. */
@Composable
private fun BoxScope.SelectedMark() {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CELL_CORNER)
    Box(
        Modifier
            .matchParentSize()
            .background(scheme.primary.copy(alpha = SELECTED_SCRIM_ALPHA), shape)
            .border(SELECTED_BORDER, scheme.primary, shape),
    )
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = scheme.onPrimary,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .size(SELECTED_ICON)
            .background(scheme.primary, CircleShape),
    )
}

/**
 * Two fingers spreading or closing past [PINCH_STEP]: [onStep] hears +1 for a
 * spread, -1 for a close, once per pinch. Seen before the grid sees it and
 * kept from it, so the grid does not scroll under a pinch; one finger passes
 * through untouched.
 */
private suspend fun PointerInputScope.detectPinchSteps(onStep: (Int) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var stepped = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (!stepped && (zoom > PINCH_STEP || zoom < 1f / PINCH_STEP)) {
                    onStep(if (zoom > 1f) 1 else -1)
                    stepped = true
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * A bar down the right edge of a long grid, laid over all of it: the thumb
 * shows where the grid is, and dragging it jumps there, with [label] for the
 * line under the thumb shown to its left while the drag lasts.
 */
@Composable
private fun FastScroller(state: LazyGridState, label: (Int) -> String?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val long by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo.size
            visible > 0 && info.totalItemsCount >= visible * FAST_SCROLL_MIN_SCREENS
        }
    }
    if (!long) return

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scrolledFraction by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val trackPixels = with(density) { (maxHeight - SCROLLER_THUMB_HEIGHT).toPx() }.coerceAtLeast(1f)
        val thumbHalf = with(density) { SCROLLER_THUMB_HEIGHT.toPx() / 2 }
        val thumbY = ((if (dragging) dragFraction else scrolledFraction) * trackPixels).roundToInt()
        fun targetIndex() = (dragFraction * (state.layoutInfo.totalItemsCount - 1)).roundToInt()

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(SCROLLER_WIDTH)
                .pointerInput(trackPixels) {
                    fun jumpTo(y: Float) {
                        dragFraction = ((y - thumbHalf) / trackPixels).coerceIn(0f, 1f)
                        val target = targetIndex()
                        scope.launch { state.scrollToItem(target) }
                    }
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            jumpTo(it.y)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            jumpTo(change.position.y)
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, thumbY) }
                    .size(SCROLLER_THUMB_WIDTH, SCROLLER_THUMB_HEIGHT)
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(SCROLLER_THUMB_WIDTH / 2),
                    ),
            )
        }

        if (dragging) {
            label(targetIndex())?.let { text ->
                // Beside the bar, not under the finger that holds it.
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(-SCROLLER_WIDTH.roundToPx(), thumbY) },
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** A day heading: the weekday, the day and the year, in the device's own way. */
private fun dayLabel(context: Context, date: LocalDate): String = DateUtils.formatDateTime(
    context,
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY or
        DateUtils.FORMAT_ABBREV_WEEKDAY,
)

/**
 * What the fast scroller shows for the grid line at [index]: the month in date
 * order, the first letter by name, the size by size. A day heading stands for
 * the files under it.
 */
private fun scrollLabel(context: Context, entries: List<GridEntry>, index: Int, order: ItemOrder): String? {
    val item = (index.coerceAtLeast(0) until entries.size)
        .firstNotNullOfOrNull { (entries[it] as? GridEntry.Item)?.item } ?: return null
    return when (order) {
        ItemOrder.DATE -> DateUtils.formatDateTime(
            context,
            item.taken,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR,
        )
        ItemOrder.NAME -> item.file.name.take(1).uppercase()
        ItemOrder.SIZE -> formatSize(context, item.size)
    }
}
