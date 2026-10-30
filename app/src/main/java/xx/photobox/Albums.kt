package xx.photobox

import java.io.File
import java.nio.file.Files

/**
 * A folder with photos or videos directly in it. [count] and [cover] cover
 * those files alone, not the folders below. [cover] is the newest of them by
 * the file's own time: the date taken would mean reading every file's EXIF on
 * every scan. [hidden] is set when the folder or one above it starts with a dot
 * or holds a .nomedia file — the same reach other galleries give .nomedia.
 */
class Album(
    val dir: File,
    val root: File,
    val count: Int,
    val cover: File,
    val modified: Long,
    val hidden: Boolean,
)

enum class AlbumOrder { NAME, DATE, COUNT }

/** Walks storage volumes for [Album]s. */
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
        var cover: File? = null
        var coverModified = Long.MIN_VALUE
        for (child in children) {
            if (child.isDirectory) {
                // The trash is reached through the trash screen, never as an
                // album; a link could lead back up the tree and never end.
                if (child.name == TRASH_DIR_NAME || Files.isSymbolicLink(child.toPath())) continue
                walk(child, root, hidden, checkCancel, found)
            } else if (isMedia(child)) {
                count++
                val modified = child.lastModified()
                if (cover == null || modified > coverModified) {
                    cover = child
                    coverModified = modified
                }
            }
        }
        if (cover != null) found += Album(dir, root, count, cover, coverModified, hidden)
    }
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
