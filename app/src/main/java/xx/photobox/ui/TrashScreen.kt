package xx.photobox.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.R
import xx.photobox.TrashEntry

private val TRASH_THUMBNAIL = 64.dp

/**
 * Everything in the trashes, newest first: what it is, where it goes back
 * to and when it was deleted. A tap on a row offers to put it back or delete
 * it for good. [entries] null means the trash is still being looked through.
 */
@Composable
fun TrashScreen(
    entries: List<TrashEntry>?,
    busy: Boolean,
    onRestore: (TrashEntry) -> Unit,
    onPurge: (TrashEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        when {
            entries == null -> ProgressState(text = null)
            entries.isEmpty() -> EmptyState(icon = null, title = stringResource(R.string.trash_empty))
            else -> {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.file.path }) { entry ->
                        TrashRow(entry, enabled = !busy, onRestore = { onRestore(entry) }, onPurge = { onPurge(entry) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashRow(entry: TrashEntry, enabled: Boolean, onRestore: () -> Unit, onPurge: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { menuOpen = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoThumbnail(entry.file.path, Modifier.size(TRASH_THUMBNAIL))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.original.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.original.parent.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.deletedAt).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatSize(context, entry.size), style = MaterialTheme.typography.bodyMedium)
        }
        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuItem(stringResource(R.string.restore)) {
                menuOpen = false
                onRestore()
            }
            AppMenuItem(stringResource(R.string.delete_forever)) {
                menuOpen = false
                confirmPurge = true
            }
        }
    }
    if (confirmPurge) {
        ConfirmDialog(
            title = stringResource(R.string.delete_forever),
            message = entry.original.name,
            onDismiss = { confirmPurge = false },
            onConfirm = {
                confirmPurge = false
                onPurge()
            },
        )
    }
}
