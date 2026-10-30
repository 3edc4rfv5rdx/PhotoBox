package xx.photobox

import java.io.File
import java.nio.file.Files

/**
 * One file in a trash, with the RAW files that went in with it. [original] is
 * where it goes back to: the folder the trash sits in, under the name it had.
 */
class TrashEntry(val file: File, val original: File, val deletedAt: Long, val companions: List<File>) {
    /** Bytes it frees for good, companions included. */
    val size: Long
        get() = file.length() + companions.sumOf { it.length() }
}

enum class RestoreResult { OK, EXISTS, FAILED }

/**
 * The app's own trash: a hidden [TRASH_DIR_NAME] folder inside each folder
 * something was deleted from, holding a .nomedia so other galleries pass it
 * by. Into it is a rename within the same folder — instant, and no space
 * needed — and back out is the same rename the other way.
 *
 * A trashed file is named `<deleted at, epoch ms>-<its name>`: the time is
 * how old it is, the rest the name it goes back under, so the trash needs no
 * record beside the files. RAW files beside a photo under its name go in with
 * it under the same time and come back with it.
 */
object Trash {

    /** [name] as it is kept in the trash, deleted at [at]. */
    fun trashName(name: String, at: Long): String = "$at-$name"

    /** The time and the original name out of a [trashName]; null for a name the trash did not write. */
    fun parseTrashName(name: String): Pair<Long, String>? {
        val dash = name.indexOf('-')
        if (dash <= 0 || dash == name.length - 1) return null
        val at = name.substring(0, dash).takeIf { part -> part.all { it.isDigit() } }?.toLongOrNull() ?: return null
        return at to name.substring(dash + 1)
    }

    /**
     * Moves [file] and its RAW companions into its folder's trash, stamped
     * [now]. True when [file] itself went in; a companion that would not is
     * left where it is rather than holding the photo back.
     */
    fun moveToTrash(file: File, now: Long): Boolean {
        val parent = file.parentFile ?: return false
        val dir = File(parent, TRASH_DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        File(dir, NOMEDIA_NAME).let { if (!it.exists()) runCatching { it.createNewFile() } }
        val companions = companionsOf(file)
        if (!file.renameTo(File(dir, trashName(file.name, now)))) return false
        companions.forEach { it.renameTo(File(dir, trashName(it.name, now))) }
        return true
    }

    /**
     * Every entry in the trashes under [roots], newest first. A RAW that went
     * in with a photo — same time, same name before the extension — is that
     * photo's companion, not an entry of its own. [checkCancel] throws to stop.
     */
    fun list(roots: List<File>, checkCancel: () -> Unit): List<TrashEntry> {
        val entries = mutableListOf<TrashEntry>()
        for (root in roots) findTrashDirs(root, checkCancel) { dir -> entries += entriesIn(dir) }
        return entries.sortedByDescending { it.deletedAt }
    }

    /** The entries of one trash folder. */
    internal fun entriesIn(dir: File): List<TrashEntry> {
        val folder = dir.parentFile ?: return emptyList()
        val parsed = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name != NOMEDIA_NAME }
            .mapNotNull { file -> parseTrashName(file.name)?.let { (at, name) -> Triple(file, at, name) } }
        val (raws, others) = parsed.partition { (_, _, name) -> isRaw(File(name)) }
        val claimed = mutableSetOf<File>()
        val entries = others.map { (file, at, name) ->
            val base = File(name).nameWithoutExtension
            val companions = raws
                .filter { (_, rawAt, rawName) -> rawAt == at && File(rawName).nameWithoutExtension == base }
                .map { it.first }
            claimed += companions
            TrashEntry(file, File(folder, name), at, companions)
        }
        // A RAW whose photo is gone from the trash stands as an entry of its own.
        val alone = raws.filter { it.first !in claimed }
            .map { (file, at, name) -> TrashEntry(file, File(folder, name), at, emptyList()) }
        return entries + alone
    }

    /**
     * Puts [entry] and its companions back. Nothing moves when any of them
     * would land on a file already there.
     */
    fun restore(entry: TrashEntry): RestoreResult {
        val moves = (listOf(entry.file) + entry.companions).map { it to originalOf(it, entry) }
        if (moves.any { (_, target) -> target.exists() }) return RestoreResult.EXISTS
        if (!entry.file.renameTo(entry.original)) return RestoreResult.FAILED
        moves.drop(1).forEach { (from, to) -> from.renameTo(to) }
        entry.file.parentFile?.let(::dropIfEmpty)
        return RestoreResult.OK
    }

    /** Deletes [entry] and its companions for good. True when the entry itself is gone. */
    fun purge(entry: TrashEntry): Boolean {
        entry.companions.forEach { it.delete() }
        val gone = entry.file.delete() || !entry.file.exists()
        entry.file.parentFile?.let(::dropIfEmpty)
        return gone
    }

    /** Deletes for good every entry under [roots] deleted before [cutoff]; how many went. */
    fun purgeOlderThan(roots: List<File>, cutoff: Long, checkCancel: () -> Unit): Int =
        list(roots, checkCancel).filter { it.deletedAt < cutoff }.count { purge(it) }

    private fun originalOf(file: File, entry: TrashEntry): File {
        val name = parseTrashName(file.name)?.second ?: file.name
        return File(entry.original.parentFile, name)
    }

    /** Removes a trash folder that holds nothing but its .nomedia any more. */
    private fun dropIfEmpty(dir: File) {
        val left = dir.listFiles() ?: return
        if (left.all { it.name == NOMEDIA_NAME }) {
            left.forEach { it.delete() }
            dir.delete()
        }
    }

    private fun findTrashDirs(dir: File, checkCancel: () -> Unit, onFound: (File) -> Unit) {
        checkCancel()
        for (child in dir.listFiles().orEmpty()) {
            if (!child.isDirectory || Files.isSymbolicLink(child.toPath())) continue
            if (child.name == TRASH_DIR_NAME) onFound(child) else findTrashDirs(child, checkCancel, onFound)
        }
    }
}
