package xx.photobox.ui

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xx.photobox.AppSettings
import xx.photobox.MediaItem
import xx.photobox.PROGRESS_TICK_MS
import xx.photobox.R
import xx.photobox.dateCache
import xx.photobox.displayNameOf
import xx.photobox.fileFromUri
import xx.photobox.hasAllFilesAccess
import xx.photobox.readItems
import xx.photobox.sortItems
import xx.photobox.takenAt
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** The files an opened one is shown among, and that one; no [items] when it could not be read at all. */
private class Opened(val items: List<MediaItem>, val start: String)

/**
 * A file another app asked to be shown, full screen. Found on storage, it
 * comes with its whole folder in the order the gallery sorts by, so a swipe
 * goes on to its neighbours; otherwise it is copied in and shown alone.
 * [onClose] when it is closed, or could not be read.
 */
@Composable
fun OpenedFileScreen(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    // Dates read in the folder so far, counted on the reading threads and shown from here.
    val done = remember { AtomicInteger(0) }
    var total by remember { mutableIntStateOf(0) }
    var shownDone by remember { mutableIntStateOf(0) }
    val opened by produceState<Opened?>(initialValue = null, uri) { value = open(context, uri, done) { total = it } }
    val shown = opened
    LaunchedEffect(shown == null) {
        if (shown != null) return@LaunchedEffect
        while (true) {
            shownDone = done.get()
            delay(PROGRESS_TICK_MS)
        }
    }
    when {
        // White on the viewer's black, whatever the theme.
        shown == null -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) { ReadingDates(shownDone, total) }
        }

        shown.items.isEmpty() -> LaunchedEffect(Unit) {
            Toast.makeText(context, R.string.open_failed, Toast.LENGTH_SHORT).show()
            onClose()
        }

        else -> ViewerScreen(
            sorted = shown.items,
            startPath = shown.start,
            onPage = {},
            onShare = { shareFiles(context, listOf(it.file)) },
            onRotate = null,
            onRename = null,
            onTransfer = null,
            onDelete = null,
            onClose = onClose,
        )
    }
}

/** [uri] found on storage with its folder, or copied in; [done] and [onToRead] count the folder's dates being read. */
private suspend fun open(context: Context, uri: Uri, done: AtomicInteger, onToRead: (Int) -> Unit): Opened {
    val onStorage = withContext(Dispatchers.IO) { if (hasAllFilesAccess()) fileFromUri(context, uri) else null }
    val file = onStorage ?: withContext(Dispatchers.IO) { copyIn(context, uri) } ?: return Opened(emptyList(), "")
    val folder = onStorage?.parentFile?.let { readItems(it, dateCache(context), done, onToRead) }.orEmpty()
    // A file the gallery does not list — an extension it does not know — is still shown, alone among the rest.
    val items = if (folder.any { it.file.path == file.path }) {
        folder
    } else {
        folder + withContext(Dispatchers.IO) { MediaItem(file, takenAt(file), file.length(), file.lastModified()) }
    }
    return Opened(sortItems(items, AppSettings.itemOrder.value), file.path)
}

/**
 * [uri]'s contents copied into the app's cache under its own name, the
 * extension made up from its type when the name has none: the viewer tells a
 * video by it. The copy before is dropped. Null when it cannot be read.
 */
private fun copyIn(context: Context, uri: Uri): File? = try {
    val dir = File(context.cacheDir, "opened").apply {
        deleteRecursively()
        mkdirs()
    }
    var name = displayNameOf(context, uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"
    if (File(name).extension.isEmpty()) {
        context.contentResolver.getType(uri)?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)?.let { name += ".$it" }
    }
    val file = File(dir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }?.let { file }
} catch (e: Exception) {
    // Revoked, gone, or a provider that will not hand it over.
    null
}
