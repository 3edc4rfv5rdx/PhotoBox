package xx.photobox

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** Each of [from] to the same place in [to], all or none: a failure puts back those already moved. */
fun renameAll(from: List<File>, to: List<File>): Boolean {
    val done = mutableListOf<Pair<File, File>>()
    for ((source, target) in from.zip(to)) {
        if (!source.renameTo(target)) {
            done.forEach { (back, now) -> now.renameTo(back) }
            return false
        }
        done.add(source to target)
    }
    return true
}

/** Why a name cannot be given — to a new folder, or to a file renamed — or [OK]. [UNCHANGED]: a file's own name. */
enum class NameCheck { OK, EMPTY, INVALID, EXISTS, UNCHANGED }

/** A name no file can have: a slash would make it a path, and "." or ".." name a folder that is already there. */
internal fun isBadName(name: String): Boolean = '/' in name || '\u0000' in name || name == "." || name == ".."

/**
 * Where renaming [file] to [base] takes it and its RAW companions: each keeps
 * its own extension, so a photo and its RAW stay a pair.
 */
fun renameTargets(file: File, base: String): List<Pair<File, File>> =
    (listOf(file) + companionsOf(file)).map { from ->
        from to File(from.parentFile, if (from.extension.isEmpty()) base else "$base.${from.extension}")
    }

/**
 * Whether [file] can be renamed to [base], the name without its extension.
 * Leading and trailing spaces do not count. A target that is the very file
 * renamed — a change of case on storage that ignores case — is no clash.
 */
fun checkRename(file: File, base: String): NameCheck {
    val trimmed = base.trim()
    return when {
        trimmed.isEmpty() -> NameCheck.EMPTY
        isBadName(trimmed) -> NameCheck.INVALID
        trimmed == file.nameWithoutExtension -> NameCheck.UNCHANGED
        renameTargets(file, trimmed).any { (from, to) -> to.exists() && !isSameFile(from, to) } -> NameCheck.EXISTS
        else -> NameCheck.OK
    }
}

internal fun isSameFile(a: File, b: File): Boolean = try {
    Files.isSameFile(a.toPath(), b.toPath())
} catch (e: IOException) {
    false
}

/** Photos whose EXIF orientation the platform library can write. */
private val ROTATABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** Whether [file] can be turned by [rotateClockwise]: not a video, nor a HEIC, whose orientation cannot be written. */
fun canRotate(file: File): Boolean = file.extension.lowercase() in ROTATABLE_EXTENSIONS

/**
 * Turns [file] 90° clockwise by its EXIF orientation alone: the pixels are
 * not touched, so nothing is lost however often it is turned. Its time is
 * kept — the grid falls back on it for a photo with no date of its own.
 */
fun rotateClockwise(file: File): Boolean = try {
    val modified = file.lastModified()
    ExifInterface(file.path).apply {
        rotate(90)
        saveAttributes()
    }
    file.setLastModified(modified)
    true
} catch (e: Exception) {
    // A malformed EXIF block throws more than IOException; the photo stays as it was.
    false
}

enum class Transfer { MOVE, COPY }

/**
 * Moves or copies [files] — a photo and its [companionsOf] — into [dir], all
 * under one name: a taken one gets " (1)", " (2)" … before the extension, on
 * every file alike, so a photo and its RAW stay a pair. Never over a file
 * already there. A move is a rename where it can be; onto another volume,
 * where a rename cannot go, it is a copy, and the originals are deleted once
 * every copy is whole. All or none: should one file not go, what was already
 * done is undone. Null on failure.
 */
fun transferGroup(files: List<File>, dir: File, mode: Transfer): Transferred? {
    if (files.isEmpty()) return null
    val targets = freeTargets(files, dir)
    if (mode == Transfer.MOVE && renameAll(files, targets)) return Transferred(targets, emptyList())
    if (!copyAll(files, targets)) return null
    val left = if (mode == Transfer.MOVE) files.filter { !it.delete() && it.exists() } else emptyList()
    return Transferred(targets, left)
}

/**
 * Where [transferGroup] put each file, in the order given, and the originals
 * a move by copy [left] where they were — copied whole, but not deleted, so
 * now in both places.
 */
class Transferred(val targets: List<File>, val left: List<File>)

/** Where [files] land in [dir] under the first number free for every one of them; none for 0. */
internal fun freeTargets(files: List<File>, dir: File): List<File> {
    var n = 0
    var targets: List<File>
    do {
        val suffix = n++
        targets = files.map { File(dir, numberedName(it.name, suffix)) }
    } while (targets.any { it.exists() })
    return targets
}

/** [name] with " ([n])" before its extension; [name] itself for 0. */
internal fun numberedName(name: String, n: Int): String {
    if (n == 0) return name
    val dot = name.lastIndexOf('.')
    // A leading dot starts a hidden name, not an extension.
    return if (dot <= 0) "$name ($n)" else "${name.substring(0, dot)} ($n)${name.substring(dot)}"
}

/** Each of [from] copied to the same place in [to], all or none: a failure deletes the copies already made. */
private fun copyAll(from: List<File>, to: List<File>): Boolean {
    val made = mutableListOf<File>()
    for ((source, target) in from.zip(to)) {
        if (!copyFile(source, target)) {
            made.forEach { it.delete() }
            return false
        }
        made.add(target)
    }
    return true
}

/**
 * [from] copied to [to], which must not exist yet, with [from]'s time kept:
 * the grid falls back on it for a file with no date of its own. A part-made
 * copy is deleted.
 */
private fun copyFile(from: File, to: File): Boolean = try {
    Files.copy(from.toPath(), to.toPath())
    to.setLastModified(from.lastModified())
    true
} catch (e: IOException) {
    // FileAlreadyExistsException among them: never over a file already there.
    if (e !is FileAlreadyExistsException) to.delete()
    false
}
