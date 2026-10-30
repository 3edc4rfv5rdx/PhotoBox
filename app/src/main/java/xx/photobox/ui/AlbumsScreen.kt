package xx.photobox.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.Album
import xx.photobox.AlbumDraft
import xx.photobox.NameCheck
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
 * with a thin bar on top while it does. A section's heading makes a new
 * album in its folder through [onNewAlbum]; with [onRemoveRoot] it can also
 * take its folder out of the search, without it the sections are whole
 * volumes, nothing to remove. An empty album's ⋮ deletes its folder through
 * [onDeleteEmpty].
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
    onNewAlbum: (File) -> Unit,
    onDeleteEmpty: (File) -> Unit,
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
    val listState = rememberLazyListState()
    // Once per visit, the first time the list is there: on to the open album,
    // with a couple of lines above it, so it is in sight without a search.
    // A rescan landing later leaves the list where the user has put it.
    var scrolledToCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(sections) {
        if (scrolledToCurrent || sections == null) return@LaunchedEffect
        scrolledToCurrent = true
        val at = listIndexOf(sections, current) ?: return@LaunchedEffect
        listState.scrollToItem((at - LINES_ABOVE_CURRENT).coerceAtLeast(0))
    }
    Column(modifier.fillMaxSize()) {
        when {
            sections == null -> ProgressState(stringResource(R.string.albums_scanning))

            else -> {
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), state = listState) {
                    // Laid out line for line as listIndexOf counts them.
                    for ((root, rootAlbums) in sections) {
                        item(key = "root:${root.path}") {
                            RootHeading(
                                root,
                                onNewAlbum = { onNewAlbum(root) },
                                onRemove = onRemoveRoot?.let { remove -> { remove(root) } },
                            )
                        }
                        if (rootAlbums.isEmpty()) {
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
                            AlbumRow(
                                album,
                                selected = album.dir == current,
                                onDelete = if (album.count == 0) {
                                    { onDeleteEmpty(album.dir) }
                                } else {
                                    null
                                },
                                onClick = { onOpen(album.dir) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** How many lines are left above the open album when the list scrolls to it. */
private const val LINES_ABOVE_CURRENT = 2

/** Where [current] sits among the list's lines — a heading per section, then its albums or one "none" line. */
private fun listIndexOf(sections: List<Pair<File, List<Album>>>, current: File?): Int? {
    if (current == null) return null
    var line = 0
    for ((_, albums) in sections) {
        line++
        if (albums.isEmpty()) {
            line++
            continue
        }
        val at = albums.indexOfFirst { it.dir == current }
        if (at >= 0) return line + at
        line += albums.size
    }
    return null
}

/** A section's heading: the folder's name and where it is, and its ⋮ to take it out of the search. */
@Composable
private fun RootHeading(root: File, onNewAlbum: () -> Unit, onRemove: (() -> Unit)?) {
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
        Box {
            MoreButton(onClick = { menuOpen = true })
            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AppMenuItem(stringResource(R.string.new_album)) {
                    menuOpen = false
                    onNewAlbum()
                }
                if (onRemove != null) {
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
private fun AlbumRow(album: Album, selected: Boolean, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    // The open album the other way round, filled with the accent, so it is
    // found at a glance in a long list.
    val scheme = MaterialTheme.colorScheme
    val background = if (selected) scheme.primary else Color.Transparent
    val content = if (selected) scheme.onPrimary else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val cover = album.cover
        if (cover != null) {
            PhotoThumbnail(cover.path, Modifier.size(ALBUM_COVER))
        } else {
            // An empty album: the app's own icon where the cover will be.
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(ALBUM_COVER)
                    .background(colorResource(R.color.ic_launcher_background), RoundedCornerShape(8.dp)),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                album.dir.name,
                style = MaterialTheme.typography.titleLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.file_count, album.count, formatCount(album.count)),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            // Two albums can share a name — Camera on the phone and on a card.
            Text(
                pathOnVolume(album.dir, album.root),
                style = MaterialTheme.typography.bodySmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDelete != null) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                MoreButton(onClick = { menuOpen = true }, tint = if (selected) content else null)
                AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AppMenuItem(stringResource(R.string.delete_album), danger = true) {
                        menuOpen = false
                        onDelete()
                    }
                }
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_album)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(draft.name, onName, check, if (failed) stringResource(R.string.album_create_failed) else null)
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
            DialogConfirmButton(stringResource(R.string.create), enabled = check == NameCheck.OK, onClick = onCreate)
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}
