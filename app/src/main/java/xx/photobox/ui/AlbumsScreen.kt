package xx.photobox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.Album
import xx.photobox.AlbumDraft
import xx.photobox.AlbumNameCheck
import xx.photobox.AlbumOrder
import xx.photobox.R
import xx.photobox.checkAlbumName
import xx.photobox.pathOnVolume
import xx.photobox.sortAlbums
import java.io.File

private val ALBUM_COVER = 72.dp

/**
 * Every album, in [order], hidden ones only with [showHidden]. [albums] null
 * means the first scan has not ended; a later scan runs under the old list,
 * with a thin bar on top while it does.
 */
@Composable
fun AlbumsScreen(
    albums: List<Album>?,
    scanning: Boolean,
    order: AlbumOrder,
    showHidden: Boolean,
    current: File?,
    onOpen: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(albums, order, showHidden) {
        albums?.let { all -> sortAlbums(if (showHidden) all else all.filterNot { it.hidden }, order) }
    }
    Column(modifier.fillMaxSize()) {
        when {
            shown == null -> ProgressState(stringResource(R.string.albums_scanning))

            else -> {
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (shown.isEmpty() && !scanning) {
                    EmptyState(icon = null, title = stringResource(R.string.albums_none))
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { it.dir.path }) { album ->
                            AlbumRow(album, selected = album.dir == current) { onOpen(album.dir) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PhotoThumbnail(album.cover.path, Modifier.size(ALBUM_COVER))
        Column(Modifier.weight(1f)) {
            Text(
                album.dir.name,
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.file_count, album.count, formatCount(album.count)),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Two albums can share a name — Camera on the phone and on a card.
            Text(
                pathOnVolume(album.dir, album.root),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The name of a new album and the folder it goes into, which [onPickParent]
 * leaves this dialog to change. Create stays off until the name would work.
 */
@Composable
fun NewAlbumDialog(
    draft: AlbumDraft,
    failed: Boolean,
    onName: (String) -> Unit,
    onPickParent: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val check = checkAlbumName(draft.parent, draft.name)
    val error = when {
        failed -> stringResource(R.string.album_create_failed)
        check == AlbumNameCheck.INVALID -> stringResource(R.string.album_name_invalid)
        check == AlbumNameCheck.EXISTS -> stringResource(R.string.album_name_exists)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_album)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onName,
                    label = { Text(stringResource(R.string.album_name)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.album_parent), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onPickParent).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Folder, stringResource(R.string.album_parent_change))
                    Text(
                        draft.parent.path,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.create), enabled = check == AlbumNameCheck.OK, onClick = onCreate)
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}
