package xx.photobox.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xx.photobox.R
import xx.photobox.storageRoots
import java.io.File

/**
 * The app's own folder chooser, in place of ACTION_OPEN_DOCUMENT_TREE. The
 * system picker makes the user confirm access to every folder handed over,
 * on every pick, for a grant this app throws away the moment it has a path.
 * Walking java.io.File under "All files access" asks nothing and reaches an
 * SD card as readily as internal storage. [start] is the folder it opens on.
 *
 * An empty [path] means the list of volumes, which is only shown when the
 * device has more than one.
 */
@Composable
fun FolderPickerScreen(start: File?, onPick: (File) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val roots = remember(context) { storageRoots(context) }
    var path by rememberSaveable {
        mutableStateOf(start?.takeIf { it.isDirectory }?.path ?: if (roots.size == 1) roots[0].dir.path else "")
    }
    val current = path.takeIf { it.isNotEmpty() }?.let(::File)
    val atRoot = roots.any { it.dir.path == path }

    Column(modifier.fillMaxSize()) {
        if (current != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Up stops at a volume's own root, or steps back to the list of
                // volumes when there is more than one to choose between.
                IconButton(
                    onClick = { path = if (atRoot) "" else current.parentFile?.path.orEmpty() },
                    enabled = !atRoot || roots.size > 1,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.folder_up))
                }
                Text(
                    text = current.path,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
        }

        if (current == null) {
            LazyColumn(Modifier.weight(1f)) {
                items(roots, key = { it.dir.path }) { root ->
                    FolderRow(name = root.label) { path = root.dir.path }
                    HorizontalDivider()
                }
            }
        } else {
            // Listed off the main thread: a folder of thousands of files takes
            // a moment to sift, the same way the photo scan does. Null while it
            // does, set first thing: produceState keeps its value across a new
            // key, and the folder just left would otherwise stay listed — and
            // tappable — under the new one's path until the listing lands.
            val children by produceState<List<File>?>(initialValue = null, key1 = path) {
                value = null
                value = withContext(Dispatchers.IO) { subFolders(File(path)) }
            }
            val listed = children
            if (listed == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (listed.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.folder_no_subfolders))
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(listed, key = { it.path }) { child ->
                        FolderRow(name = child.name) { path = child.path }
                        HorizontalDivider()
                    }
                }
            }
        }

        if (current != null) {
            HorizontalDivider()
            Button(
                onClick = { onPick(current) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.folder_use_this), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Folder, null, Modifier.size(28.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dot-directories are left out: a new album or a file put into one would be
 * hidden from the album list by default. */
private fun subFolders(dir: File): List<File> =
    dir.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .sortedBy { it.name.lowercase() }
