package xx.photobox.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * Every album, one section per folder of [roots] it was found under, each
 * section in [order], hidden albums only with [showHidden]. [albums] null
 * means the first scan has not ended; a later scan runs under the old list,
 * with a thin bar on top while it does. With [onRemoveRoot] a section's
 * heading can take its folder out of the search; without it the sections are
 * whole volumes, nothing to remove.
 */
@Composable
fun AlbumsScreen(
    albums: List<Album>?,
    roots: List<File>,
    scanning: Boolean,
    order: AlbumOrder,
    showHidden: Boolean,
    current: File?,
    onOpen: (File) -> Unit,
    onRemoveRoot: ((File) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sections = remember(albums, roots, order, showHidden) {
        albums?.let { all ->
            val visible = if (showHidden) all else all.filterNot { it.hidden }
            val byRoot = visible.groupBy { it.root }
            roots.map { root -> root to sortAlbums(byRoot[root].orEmpty(), order) }
        }
    }
    Column(modifier.fillMaxSize()) {
        when {
            sections == null -> ProgressState(stringResource(R.string.albums_scanning))

            else -> {
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f)) {
                    for ((root, rootAlbums) in sections) {
                        item(key = "root:${root.path}") {
                            RootHeading(root, onRemove = onRemoveRoot?.let { remove -> { remove(root) } })
                        }
                        if (rootAlbums.isEmpty() && !scanning) {
                            item(key = "empty:${root.path}") {
                                Text(
                                    stringResource(if (root.isDirectory) R.string.root_empty else R.string.root_missing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        items(rootAlbums, key = { it.dir.path }) { album ->
                            AlbumRow(album, selected = album.dir == current) { onOpen(album.dir) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** A section's heading: the folder's name and where it is, and its ⋮ to take it out of the search. */
@Composable
private fun RootHeading(root: File, onRemove: (() -> Unit)?) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                root.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                root.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            Box {
                MoreButton(onClick = { menuOpen = true })
                AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AppMenuItem(stringResource(R.string.root_remove)) {
                        menuOpen = false
                        onRemove()
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
