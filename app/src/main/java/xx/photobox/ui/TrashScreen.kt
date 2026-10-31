package xx.photobox.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.R
import xx.photobox.TrashEntry
import xx.photobox.TrashJob

private val TRASH_THUMBNAIL = 64.dp

/**
 * Everything in the trashes, newest first, under a line saying how many
 * files that is and how much room they take. Each row says what the file is,
 * where it goes back to and when it was deleted; a tap opens it full screen
 * through [onView], and its ⋮ puts it back or deletes it for good. The row
 * [viewed] last stands out, so it is plain which one was looked at. [entries]
 * null means the trash is still being looked through; while [job] runs, its
 * progress takes the list's place.
 */
@Composable
fun TrashScreen(
    entries: List<TrashEntry>?,
    busy: Boolean,
    job: TrashJob?,
    jobDone: Int,
    jobTotal: Int,
    viewed: String?,
    onView: (TrashEntry) -> Unit,
    onRestore: (TrashEntry) -> Unit,
    onPurge: (TrashEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // By path: an entry object does not outlive a reload of the trash.
    var purgePath by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    when {
        job != null -> {
            ProgressState(
                text = stringResource(
                    if (job == TrashJob.RESTORE) R.string.trash_restoring else R.string.trash_emptying,
                    formatCount(jobDone),
                    formatCount(jobTotal),
                ),
                modifier = modifier.fillMaxSize(),
                progress = { if (jobTotal > 0) jobDone.toFloat() / jobTotal else 0f },
            )
            return
        }
        entries == null -> {
            ProgressState(text = null, modifier = modifier.fillMaxSize())
            return
        }
        entries.isEmpty() -> {
            Box(modifier.fillMaxSize()) { EmptyState(icon = null, title = stringResource(R.string.trash_empty)) }
            return
        }
    }
    val listed = entries.orEmpty()

    Column(modifier.fillMaxSize()) {
        Text(
            "${pluralStringResource(R.plurals.file_count, listed.size, formatCount(listed.size))}  ·  " +
                formatSize(context, listed.sumOf { it.size }),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
        LazyColumn(Modifier.weight(1f).mouseDragScroll(listState), state = listState) {
            items(listed, key = { it.file.path }) { entry ->
                TrashRow(
                    entry = entry,
                    viewed = entry.file.path == viewed,
                    enabled = !busy,
                    onView = { onView(entry) },
                    onRestore = { onRestore(entry) },
                    onPurge = { purgePath = entry.file.path },
                )
                HorizontalDivider()
            }
        }
    }

    listed.firstOrNull { it.file.path == purgePath }?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.delete_forever),
            message = "${entry.original.name}  ·  ${formatSize(context, entry.size)}",
            onDismiss = { purgePath = null },
            onConfirm = {
                purgePath = null
                onPurge(entry)
            },
        )
    }
}

@Composable
private fun TrashRow(
    entry: TrashEntry,
    viewed: Boolean,
    enabled: Boolean,
    onView: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // The one looked at last the other way round, the same as the open album in the album list.
    val text = if (viewed) scheme.onPrimary else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (viewed) scheme.primary else Color.Transparent)
            .clickable(onClick = onView)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A trashed file's name is the moment it was trashed: no other file is ever put under it.
        PhotoThumbnail(entry.file.path, size = 0, modified = entry.deletedAt, modifier = Modifier.size(TRASH_THUMBNAIL))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                entry.original.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.original.parent.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${DateUtils.getRelativeTimeSpanString(entry.deletedAt)}  ·  ${formatSize(context, entry.size)}",
                style = MaterialTheme.typography.bodyMedium,
                color = text,
            )
        }
        EntryMenu(enabled = enabled, tint = if (viewed) text else null, onRestore = onRestore, onPurge = onPurge)
    }
}

/** The actions on one entry, behind its own ⋮. */
@Composable
private fun EntryMenu(enabled: Boolean, tint: Color?, onRestore: () -> Unit, onPurge: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MoreButton(onClick = { open = true }, enabled = enabled, tint = tint)
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            AppMenuItem(stringResource(R.string.restore)) {
                open = false
                onRestore()
            }
            AppMenuItem(stringResource(R.string.delete_forever), danger = true) {
                open = false
                onPurge()
            }
        }
    }
}
