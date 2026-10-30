package xx.photobox.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.AlbumOrder
import xx.photobox.AppSettings
import xx.photobox.GalleryViewModel
import xx.photobox.ItemOrder
import xx.photobox.MediaItem
import xx.photobox.R
import xx.photobox.TrashNotice
import xx.photobox.isVideo
import xx.photobox.sortItems

/** The screens besides the album's thumbnails, each opened from the ⋮ menu or the title. */
private enum class Screen { THUMBNAILS, ALBUMS, NEW_ALBUM_PARENT, ADD_ROOT, TRASH, SETTINGS }

/**
 * The whole app: the open album's thumbnails, with ✕ to leave on the left, the
 * album's name in the middle opening the album list, and ⋮ on the right for
 * everything else.
 */
@Composable
fun MainScreen(
    vm: GalleryViewModel,
    onGrantAccess: () -> Unit,
    onExit: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val albumOrder by AppSettings.albumOrder.collectAsState()
    val showHidden by AppSettings.showHidden.collectAsState()
    val itemOrder by AppSettings.itemOrder.collectAsState()
    val gridColumns by AppSettings.gridColumns.collectAsState()
    // Saveable: picking a language recreates the activity, and a plain
    // remember would drop the user back on the thumbnails mid-Settings.
    // With nothing to show — a first launch without a camera folder — the
    // album list is where to start.
    var screen by rememberSaveable {
        mutableStateOf(if (vm.hasStorageAccess && vm.album == null) Screen.ALBUMS else Screen.THUMBNAILS)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var choosingOrder by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf<TrashStep?>(null) }
    // The trashed file open full screen, and the one looked at last, marked in the list.
    var trashViewerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var trashViewed by rememberSaveable { mutableStateOf<String?>(null) }
    val trashItems = remember(vm.trashEntries) {
        vm.trashEntries.orEmpty().map { MediaItem(it.file, it.deletedAt, it.size) }
    }
    val roots by AppSettings.roots.collectAsState()
    var choosingItemOrder by remember { mutableStateOf(false) }
    // The file open full screen, by path: the list under it can be reloaded
    // and shift every index, but the file stays the one it was.
    var viewerPath by rememberSaveable { mutableStateOf<String?>(null) }
    val items = vm.items
    val sorted = remember(items, itemOrder) { items?.let { sortItems(it, itemOrder) } }
    val photos = remember(sorted) { sorted?.filterNot { isVideo(it.file.path) }.orEmpty() }
    // The files picked by a long tap, by path. Keyed by the album, so another one opens with none picked.
    var selection by rememberSaveable(vm.album?.path) { mutableStateOf(emptySet<String>()) }
    val picked = remember(sorted, selection) { sorted?.filter { it.file.path in selection }.orEmpty() }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    val slideSeconds by AppSettings.slideSeconds.collectAsState()
    // Where the running slideshow started; null while none runs.
    var slideshowStart by rememberSaveable { mutableStateOf<Int?>(null) }
    // Where the album's last slideshow got to, while asked whether to go on from there.
    var slideshowResume by rememberSaveable { mutableStateOf<Int?>(null) }
    val backToThumbnails = { screen = Screen.THUMBNAILS }

    LaunchedEffect(screen) {
        if (screen == Screen.ALBUMS) vm.scanAlbums()
        if (screen == Screen.TRASH) vm.loadTrash()
    }
    vm.trashNotice?.let { notice ->
        LaunchedEffect(notice) {
            Toast.makeText(context, notice.messageRes(), Toast.LENGTH_SHORT).show()
            vm.noticeShown()
        }
    }
    LaunchedEffect(vm.album, vm.hasStorageAccess) { vm.loadItems() }
    // A file gone from the album — to the trash, or deleted elsewhere — is no longer picked.
    LaunchedEffect(sorted) {
        sorted?.let { list ->
            val paths = list.mapTo(HashSet()) { it.file.path }
            if (!paths.containsAll(selection)) selection = selection.filterTo(LinkedHashSet()) { it in paths }
        }
    }

    Box(modifier.fillMaxSize()) {
        when (screen) {
            Screen.ALBUMS -> AppScreen(
                title = stringResource(R.string.albums),
                onBack = backToThumbnails,
                actions = {
                    LargeIconButton(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort), { choosingOrder = true })
                    Box {
                        LargeIconButton(Icons.Filled.Add, stringResource(R.string.add), { addMenuOpen = true })
                        AppMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                            AppMenuItem(stringResource(R.string.new_album)) {
                                addMenuOpen = false
                                vm.startDraft()
                            }
                            AppMenuItem(stringResource(R.string.add_root)) {
                                addMenuOpen = false
                                screen = Screen.ADD_ROOT
                            }
                        }
                    }
                },
            ) {
                AlbumsScreen(
                    albums = vm.albums,
                    roots = vm.scannedRoots,
                    scanning = vm.scanning,
                    order = albumOrder,
                    showHidden = showHidden,
                    current = vm.album,
                    onOpen = {
                        vm.openAlbum(it)
                        screen = Screen.THUMBNAILS
                    },
                    // Whole volumes, searched for want of roots, are not the user's to remove.
                    onRemoveRoot = if (roots.isEmpty()) {
                        null
                    } else {
                        { root ->
                            AppSettings.removeRoot(context, root.path)
                            vm.scanAlbums(restart = true)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Screen.ADD_ROOT -> AppScreen(
                title = stringResource(R.string.add_root),
                onBack = { screen = Screen.ALBUMS },
            ) {
                FolderPickerScreen(
                    start = null,
                    onPick = {
                        AppSettings.addRoot(context, it.path)
                        vm.scanAlbums(restart = true)
                        screen = Screen.ALBUMS
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Screen.NEW_ALBUM_PARENT -> AppScreen(
                title = stringResource(R.string.album_parent),
                onBack = { screen = Screen.ALBUMS },
            ) {
                FolderPickerScreen(
                    start = vm.draft?.parent,
                    onPick = {
                        vm.setDraftParent(it)
                        screen = Screen.ALBUMS
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Screen.TRASH -> AppScreen(
                title = stringResource(R.string.trash),
                onBack = backToThumbnails,
                actions = {
                    val filled = !vm.trashBusy && !vm.trashEntries.isNullOrEmpty()
                    LargeIconButton(
                        Icons.Filled.RestoreFromTrash,
                        stringResource(R.string.restore_all),
                        { confirmTrash = TrashStep.RESTORE_ALL },
                        enabled = filled,
                    )
                    DialogConfirmButton(stringResource(R.string.empty_trash), danger = true, enabled = filled) {
                        confirmTrash = TrashStep.EMPTY
                    }
                },
            ) {
                Box(Modifier.weight(1f)) {
                    TrashScreen(
                        entries = vm.trashEntries,
                        busy = vm.trashBusy,
                        job = vm.trashJob,
                        jobDone = vm.trashJobDone,
                        jobTotal = vm.trashJobTotal,
                        viewed = trashViewed,
                        onView = { trashViewerPath = it.file.path },
                        onRestore = vm::restore,
                        onPurge = vm::purge,
                    )
                }
            }

            Screen.SETTINGS -> AppScreen(
                title = stringResource(R.string.settings),
                onBack = backToThumbnails,
                actions = {
                    LargeIconButton(Icons.Outlined.Info, stringResource(R.string.about), onAbout)
                },
            ) {
                SettingsScreen(modifier = Modifier.weight(1f))
            }

            Screen.THUMBNAILS -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    if (selection.isNotEmpty()) {
                        BackHandler { selection = emptySet() }
                        TopBar {
                            // Not ✕: that is the exit, a tap away from here once the pick is dropped.
                            LargeIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { selection = emptySet() })
                            Text(
                                text = stringResource(R.string.selected_count, formatCount(selection.size)),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                            LargeIconButton(
                                Icons.Filled.Share,
                                stringResource(R.string.share),
                                { shareFiles(context, picked.map { it.file }) },
                            )
                            LargeIconButton(
                                Icons.Filled.Delete,
                                stringResource(R.string.to_trash),
                                { vm.moveToTrash(picked.map { it.file }) },
                                enabled = !vm.trashBusy,
                            )
                            Box {
                                MoreButton(onClick = { selectionMenuOpen = true })
                                AppMenu(expanded = selectionMenuOpen, onDismissRequest = { selectionMenuOpen = false }) {
                                    AppMenuItem(stringResource(R.string.select_all)) {
                                        selectionMenuOpen = false
                                        selection = sorted.orEmpty().mapTo(LinkedHashSet()) { it.file.path }
                                    }
                                }
                            }
                        }
                    } else TopBar {
                        LargeIconButton(Icons.Filled.Close, stringResource(R.string.exit), onExit)
                        // The quick way to another album.
                        Text(
                            text = vm.album?.name ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = vm.hasStorageAccess) { screen = Screen.ALBUMS }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                        Box {
                            MoreButton(onClick = { menuOpen = true })
                            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                AppMenuItem(stringResource(R.string.albums), enabled = vm.hasStorageAccess) {
                                    menuOpen = false
                                    screen = Screen.ALBUMS
                                }
                                AppMenuItem(stringResource(R.string.trash), enabled = vm.hasStorageAccess) {
                                    menuOpen = false
                                    screen = Screen.TRASH
                                }
                                AppMenuItem(stringResource(R.string.sort), enabled = vm.album != null) {
                                    menuOpen = false
                                    choosingItemOrder = true
                                }
                                AppMenuItem(stringResource(R.string.slideshow), enabled = photos.isNotEmpty()) {
                                    menuOpen = false
                                    val last = vm.album?.let { AppSettings.slideshowFrame(context, it) }
                                    val at = photos.indexOfFirst { it.file.path == last }
                                    // From the first photo there is nothing to choose between.
                                    if (at > 0) slideshowResume = at else slideshowStart = 0
                                }
                                AppMenuItem(stringResource(R.string.settings)) {
                                    menuOpen = false
                                    screen = Screen.SETTINGS
                                }
                            }
                        }
                    }
                    when {
                        !vm.hasStorageAccess -> EmptyState(
                            icon = null,
                            title = stringResource(R.string.need_storage_access),
                            message = stringResource(R.string.need_storage_access_hint),
                            action = { Button(onClick = onGrantAccess) { Text(stringResource(R.string.grant_access)) } },
                        )

                        vm.album == null -> EmptyState(
                            icon = null,
                            title = stringResource(R.string.no_album_open),
                            action = { Button(onClick = { screen = Screen.ALBUMS }) { Text(stringResource(R.string.albums)) } },
                        )

                        // Keyed by the album, so another one opens scrolled to its top.
                        else -> key(vm.album) {
                            ThumbnailsScreen(
                                sorted = sorted,
                                datesDone = vm.datesDone,
                                datesTotal = vm.datesTotal,
                                order = itemOrder,
                                columns = gridColumns,
                                onColumns = { AppSettings.setGridColumns(context, it) },
                                selected = selection,
                                onOpen = { viewerPath = it.file.path },
                                onSelect = { item ->
                                    val path = item.file.path
                                    selection = if (path in selection) selection - path else selection + path
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Over the grid, not in its place, so the grid keeps where it
                // was scrolled to for when the viewer closes.
                val open = viewerPath
                if (open != null && !sorted.isNullOrEmpty() && vm.album != null) {
                    // A reloaded list can put another file at the same index:
                    // the viewer starts over on the file it was showing.
                    key(sorted) {
                        ViewerScreen(
                            sorted = sorted,
                            startPath = open,
                            onPage = { viewerPath = it },
                            onShare = { shareFiles(context, listOf(it.file)) },
                            onDelete = { item ->
                                // One at a time: a second tap while the first is under way does nothing.
                                if (!vm.trashBusy) {
                                    // On to a neighbour first, so the viewer opens on it once
                                    // the album has been read again without this file.
                                    val at = sorted.indexOf(item)
                                    viewerPath = (sorted.getOrNull(at + 1) ?: sorted.getOrNull(at - 1))?.file?.path
                                    vm.moveToTrash(listOf(item.file)) { moved -> if (!moved) viewerPath = item.file.path }
                                }
                            },
                            onClose = { viewerPath = null },
                        )
                    }
                }
                val start = slideshowStart
                if (start != null && photos.isNotEmpty()) {
                    SlideshowScreen(
                        photos = photos,
                        start = start,
                        seconds = slideSeconds,
                        onShown = { path -> vm.album?.let { AppSettings.setSlideshowFrame(context, it, path) } },
                        onClose = { slideshowStart = null },
                    )
                }
            }
        }
    }

    // Over the trash list, so it keeps its scroll; swipes through the trash. Nothing to send or
    // trash in its menu: a trashed file is named by when it was deleted, not by its own name.
    val trashOpen = trashViewerPath
    if (screen == Screen.TRASH && trashOpen != null && trashItems.isNotEmpty()) {
        key(trashItems) {
            ViewerScreen(
                sorted = trashItems,
                startPath = trashOpen,
                onPage = {
                    trashViewerPath = it
                    trashViewed = it
                },
                onShare = null,
                onDelete = null,
                onClose = { trashViewerPath = null },
            )
        }
    }

    confirmTrash?.let { step ->
        ConfirmDialog(
            title = stringResource(if (step == TrashStep.EMPTY) R.string.empty_trash else R.string.restore_all),
            message = stringResource(
                if (step == TrashStep.EMPTY) R.string.empty_trash_confirm else R.string.restore_all_confirm,
            ),
            danger = step == TrashStep.EMPTY,
            onDismiss = { confirmTrash = null },
            onConfirm = {
                confirmTrash = null
                if (step == TrashStep.EMPTY) vm.emptyTrash() else vm.restoreAll()
            },
        )
    }

    slideshowResume?.let { at ->
        AlertDialog(
            onDismissRequest = { slideshowResume = null },
            title = { Text(stringResource(R.string.slideshow)) },
            confirmButton = {
                DialogConfirmButton(stringResource(R.string.slideshow_continue)) {
                    slideshowResume = null
                    slideshowStart = at
                }
            },
            dismissButton = {
                DialogDismissButton(stringResource(R.string.slideshow_from_start)) {
                    slideshowResume = null
                    slideshowStart = 0
                }
            },
        )
    }

    if (screen == Screen.THUMBNAILS && choosingItemOrder) {
        ChoiceDialog(
            title = stringResource(R.string.sort),
            options = ItemOrder.entries,
            selected = itemOrder,
            label = { stringResource(it.labelRes()) },
            onDismiss = { choosingItemOrder = false },
            onPick = {
                AppSettings.setItemOrder(context, it)
                choosingItemOrder = false
            },
        )
    }

    if (screen == Screen.ALBUMS) {
        vm.draft?.let { draft ->
            NewAlbumDialog(
                draft = draft,
                failed = vm.createFailed,
                onName = vm::editDraft,
                onPickParent = { screen = Screen.NEW_ALBUM_PARENT },
                onCreate = { vm.createAlbum(onCreated = backToThumbnails) },
                onDismiss = vm::cancelDraft,
            )
        }
        if (choosingOrder) {
            ChoiceDialog(
                title = stringResource(R.string.sort),
                options = AlbumOrder.entries,
                selected = albumOrder,
                label = { stringResource(it.labelRes()) },
                onDismiss = { choosingOrder = false },
                onPick = {
                    AppSettings.setAlbumOrder(context, it)
                    choosingOrder = false
                },
            )
        }
    }
}

/** The thumbnails' top bar, in either of its two forms: the album's, or the picked files'. */
@Composable
private fun TopBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A step on the whole trash, waiting on its OK. */
private enum class TrashStep { RESTORE_ALL, EMPTY }

private fun TrashNotice.messageRes(): Int = when (this) {
    TrashNotice.DELETE_FAILED -> R.string.delete_failed
    TrashNotice.RESTORE_EXISTS -> R.string.restore_exists
    TrashNotice.RESTORE_FAILED -> R.string.restore_failed
    TrashNotice.RESTORE_SOME_LEFT -> R.string.restore_some_left
}

private fun ItemOrder.labelRes(): Int = when (this) {
    ItemOrder.DATE -> R.string.order_date
    ItemOrder.NAME -> R.string.order_name
    ItemOrder.SIZE -> R.string.order_size
}

private fun AlbumOrder.labelRes(): Int = when (this) {
    AlbumOrder.NAME -> R.string.order_name
    AlbumOrder.DATE -> R.string.order_date
    AlbumOrder.COUNT -> R.string.order_count
}
