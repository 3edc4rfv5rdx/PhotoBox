package xx.photobox

import java.io.File
import java.nio.file.Files

/**
 * A folder with photos or videos directly in it, or with nothing in it at all
 * — a new album, waiting for files. [count] and [cover] cover those files
 * alone, not the folders below. [cover] is the newest of them by the file's
 * own time: the date taken would mean reading every file's EXIF on every
 * scan; null for an empty album, whose [modified] is the folder's own. [hidden] is set when the folder or one above it starts with a dot
 * or holds a .nomedia file — the same reach other galleries give .nomedia.
 */
class Album(
    val dir: File,
    val root: File,
    val count: Int,
    val cover: File?,
    val modified: Long,
    val hidden: Boolean,
)

enum class AlbumOrder { NAME, DATE, COUNT }

/** Walks the folders albums are looked for in — the search roots, or whole storage volumes — for [Album]s. */
object AlbumScanner {

    /** Every album under [roots], in no particular order. [checkCancel] throws to stop the walk. */
    fun scan(roots: List<File>, checkCancel: () -> Unit): List<Album> {
        val found = mutableListOf<Album>()
        for (root in roots) walk(root, root, hiddenAbove = false, checkCancel, found)
        return found
    }

    private fun walk(dir: File, root: File, hiddenAbove: Boolean, checkCancel: () -> Unit, found: MutableList<Album>) {
        checkCancel()
        // Null for a folder the app may not read, Android/data among them.
        val children = dir.listFiles() ?: return
        val hidden = hiddenAbove || (dir != root && dir.name.startsWith(".")) ||
            children.any { it.name.equals(NOMEDIA_NAME, ignoreCase = true) }
        var count = 0
        var music = false
        var cover: File? = null
        var coverModified = Long.MIN_VALUE
        for (child in children) {
            if (child.isDirectory) {
                // The trash is reached through the trash screen, never as an
                // album; a link could lead back up the tree and never end.
                if (Trash.isTrashDir(child) || Files.isSymbolicLink(child.toPath())) continue
                walk(child, root, hidden, checkCancel, found)
            } else if (isAudio(child)) {
                music = true
            } else if (isMedia(child)) {
                count++
                val modified = child.lastModified()
                if (cover == null || modified > coverModified) {
                    cover = child
                    coverModified = modified
                }
            }
        }
        // A music album's pictures are its covers, not photos anyone is after;
        // the folders below it are still looked through. An empty folder is
        // an album just made — but not a root, which is a section of its own.
        when {
            music -> {}
            cover != null -> found += Album(dir, root, count, cover, coverModified, hidden)
            children.isEmpty() && dir != root -> found += Album(dir, root, 0, null, dir.lastModified(), hidden)
        }
    }
}

/**
 * [paths] as search roots: each once, in the order given, and none that lies
 * inside another — its albums are found under that one already, and would
 * otherwise be listed twice.
 */
fun normalizeRoots(paths: List<String>): List<String> {
    val clean = paths.map { it.trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
    return clean.filter { path -> clean.none { other -> other != path && path.startsWith("$other/") } }
}

/**
 * [albums] in [order]: names A to Z, the newest or the fullest first. Ties
 * fall back to the path, so the list does not reshuffle between two scans.
 */
fun sortAlbums(albums: List<Album>, order: AlbumOrder): List<Album> {
    val byPath = compareBy<Album> { it.dir.path }
    val comparator = when (order) {
        AlbumOrder.NAME -> compareBy<Album> { it.dir.name.lowercase() }.then(byPath)
        AlbumOrder.DATE -> compareByDescending<Album> { it.modified }.then(byPath)
        AlbumOrder.COUNT -> compareByDescending<Album> { it.count }.then(byPath)
    }
    return albums.sortedWith(comparator)
}

/** Why a name cannot become a new album in its folder, or [OK]. */
enum class AlbumNameCheck { OK, EMPTY, INVALID, EXISTS }

/**
 * Whether a folder named [name] can be made in [parent]. Leading and trailing
 * spaces do not count. A slash would make a path, and "." or ".." name a folder
 * that is already there.
 */
fun checkAlbumName(parent: File, name: String): AlbumNameCheck {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> AlbumNameCheck.EMPTY
        '/' in trimmed || '\u0000' in trimmed || trimmed == "." || trimmed == ".." -> AlbumNameCheck.INVALID
        File(parent, trimmed).exists() -> AlbumNameCheck.EXISTS
        else -> AlbumNameCheck.OK
    }
}
