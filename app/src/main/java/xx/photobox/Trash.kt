package xx.photobox

import java.io.File
import java.nio.file.Files

/** Where each storage volume keeps its trash, from the volume's root. */
const val TRASH_PATH = "Documents/PhotoBox/.Trash"

/**
 * How old a record whose file is missing must be before a listing drops it.
 * A trashing writes its record first and renames the file in after: a younger
 * record may still be waiting for its file.
 */
const val TRASH_RECORD_GRACE_MS = 10 * 60 * 1000L

/** Where a file found in a trash with no record goes back to, on its own volume: nothing says where it came from. */
const val TRASH_ORPHAN_RESTORE_PATH = "Pictures/Restored"

/**
 * The folder the trash used to be, one inside every folder something was
 * deleted from. Read only to carry what is in them over to [TRASH_PATH].
 */
private const val LEGACY_TRASH_DIR_NAME = ".PhotoTrash"

/**
 * One file in a trash, with the RAW files that went in with it. [original]
 * is where it goes back to; each companion is paired with its own.
 */
class TrashEntry(
    val file: File,
    val record: File,
    val original: File,
    val deletedAt: Long,
    val companions: List<Pair<File, File>>,
) {
    /** Bytes it frees for good, companions included. */
    val size: Long
        get() = file.length() + companions.sumOf { it.first.length() }
}

enum class RestoreResult { OK, EXISTS, FAILED }

/**
 * The app's own trash, one per storage volume at [TRASH_PATH], holding a
 * .nomedia so other galleries pass it by. Per volume, not one for the phone:
 * into it is a rename on the same volume — instant, and no space needed —
 * where a move from an SD card to internal storage would be a copy.
 *
 *   <id>.<ext>        the file, renamed
 *   <id>-c1.<ext>     a RAW that went with it
 *   <id>.path         the record: one line per file, stored name, a tab, the path it came from
 *
 * The id is the moment of deletion in epoch milliseconds, "-1", "-2" added in
 * the rare case two land on the same one, so no two files ever collide
 * whatever their names were. The file keeps its extension, so a file manager
 * still sees a photo.
 */
object Trash {
    private const val RECORD_SUFFIX = ".path"

    /** The trash on the volume whose root is [volume]. */
    fun dirOn(volume: File): File = File(volume, TRASH_PATH)

    /** Whether [dir] is a trash, current or old, so a walk for albums passes it by. */
    fun isTrashDir(dir: File): Boolean = dir.name == LEGACY_TRASH_DIR_NAME || dir.path.endsWith("/$TRASH_PATH")

    /** Moves [file] and its RAW companions into [trashDir], stamped [now], all or none. */
    fun moveToTrash(file: File, trashDir: File, now: Long): Boolean =
        store(listOf(file to file) + companionsOf(file).map { it to it }, trashDir, now)

    /**
     * Moves each file of [files] — the entry's own first, then its companions
     * — into [trashDir] under id [at], each paired with the path it is to go
     * back to. The record is written first: a file in the trash with no
     * record could never go back.
     */
    private fun store(files: List<Pair<File, File>>, trashDir: File, at: Long): Boolean {
        if (!trashDir.isDirectory && !trashDir.mkdirs()) return false
        File(trashDir, NOMEDIA_NAME).let { if (!it.exists()) runCatching { it.createNewFile() } }
        val id = freeId(trashDir, at, files.map { it.second })
        val placed = files.mapIndexed { k, (from, original) ->
            Triple(from, File(trashDir, storedName(storedId(id, k), original)), original)
        }
        val record = File(trashDir, id + RECORD_SUFFIX)
        val text = placed.joinToString("") { (_, stored, original) -> "${stored.name}\t${original.path}\n" }
        if (runCatching { record.writeText(text) }.isFailure) return false
        if (!renameAll(placed.map { it.first }, placed.map { it.second })) {
            record.delete()
            return false
        }
        return true
    }

    /** The stored name's stem of the [k]-th file of entry [id]: the entry's own is the id, a companion's carries -c<k>. */
    private fun storedId(id: String, k: Int): String = if (k == 0) id else "$id-c$k"

    private fun storedName(id: String, original: File): String =
        if (original.extension.isEmpty()) id else "$id.${original.extension}"

    /**
     * The id for entry stamped [at]: the time, or with "-1", "-2" when taken.
     * The files' names are checked too, not only the record: a rename onto a
     * stray file of the same name would silently replace it.
     */
    private fun freeId(trashDir: File, at: Long, originals: List<File>): String {
        var id = at.toString()
        var n = 0
        fun taken() = File(trashDir, id + RECORD_SUFFIX).exists() ||
            originals.withIndex().any { (k, original) -> File(trashDir, storedName(storedId(id, k), original)).exists() }
        while (taken()) id = "$at-${++n}"
        return id
    }

    /**
     * Every entry in [trashDirs], newest first. A record whose file has been
     * gone longer than [TRASH_RECORD_GRACE_MS] at [now] is dropped on the way;
     * a younger one is passed over, since its file may be on its way in. A file
     * that no record names is listed too, to go back to [TRASH_ORPHAN_RESTORE_PATH].
     */
    fun list(trashDirs: List<File>, now: Long = System.currentTimeMillis()): List<TrashEntry> =
        trashDirs.flatMap { dir -> listDir(dir, now) }.sortedByDescending { it.deletedAt }

    private fun listDir(dir: File, now: Long): List<TrashEntry> {
        // The files are looked at before the records are read: a record is whole
        // before its files are renamed in, so every file seen here that a record
        // names is named by the time that record is read.
        val seen = dir.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }
        val (records, files) = seen.partition { it.name.endsWith(RECORD_SUFFIX) }
        val read = records.map { readRecord(it) }
        val entries = read.mapNotNull { (record, lines) -> entryOf(record, lines, now) }
        val named = read.flatMap { (_, lines) -> lines.map { it.first.name } }.toHashSet()
        // Still there after the records were read: not one a restore or a purge has just taken out.
        val orphans = files.filter { it.name !in named && it.exists() }.map { orphanEntry(it, dir) }
        return entries + orphans
    }

    /** The lines of [record]: each stored file with the path it came from; none when it cannot be read. */
    private fun readRecord(record: File): Pair<File, List<Pair<File, File>>> {
        val dir = record.parentFile ?: return record to emptyList()
        val lines = runCatching { record.readLines() }.getOrNull()
            ?.mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 } }
            ?.map { (stored, original) -> File(dir, stored) to File(original) }
            .orEmpty()
        return record to lines
    }

    private fun entryOf(record: File, lines: List<Pair<File, File>>, now: Long): TrashEntry? {
        val id = record.name.removeSuffix(RECORD_SUFFIX)
        val deletedAt = id.substringBefore('-').toLongOrNull() ?: return null
        val (item, original) = lines.firstOrNull() ?: return null
        if (!item.exists()) {
            // Its file was deleted by hand: nothing is left to restore. Unless it
            // is young, and the file not renamed in yet.
            if (now - record.lastModified() >= TRASH_RECORD_GRACE_MS) record.delete()
            return null
        }
        return TrashEntry(item, record, original, deletedAt, lines.drop(1).filter { it.first.exists() })
    }

    /**
     * A file in [dir] that no record names, left so by an older build: shown
     * under its stored name, deleted when its stem's time is past, and put back
     * in [TRASH_ORPHAN_RESTORE_PATH] on its volume. It has no record, and the
     * one it is given is a name no record can have, so a restore or a purge
     * deleting it touches nothing.
     */
    private fun orphanEntry(file: File, dir: File): TrashEntry {
        val volume = if (dir.path.endsWith("/$TRASH_PATH")) File(dir.path.removeSuffix("/$TRASH_PATH")) else dir.parentFile ?: dir
        val deletedAt = file.nameWithoutExtension.substringBefore('-').toLongOrNull() ?: file.lastModified()
        val record = File(dir, ".no-record-${file.name}")
        return TrashEntry(file, record, File(File(volume, TRASH_ORPHAN_RESTORE_PATH), file.name), deletedAt, emptyList())
    }

    /**
     * Puts [entry] and its companions back, making their folder again if it
     * was deleted meanwhile. Nothing moves when any of them would land on a
     * file already there.
     */
    fun restore(entry: TrashEntry): RestoreResult {
        val moves = listOf(entry.file to entry.original) + entry.companions
        if (moves.any { (_, target) -> target.exists() }) return RestoreResult.EXISTS
        val folder = entry.original.parentFile ?: return RestoreResult.FAILED
        if (!folder.isDirectory && !folder.mkdirs()) return RestoreResult.FAILED
        if (!renameAll(moves.map { it.first }, moves.map { it.second })) return RestoreResult.FAILED
        entry.record.delete()
        return RestoreResult.OK
    }

    /** Deletes [entry] and its companions for good. True when the entry itself is gone. */
    fun purge(entry: TrashEntry): Boolean {
        // The file first: when it will not go, its RAW stays with it.
        val gone = entry.file.delete() || !entry.file.exists()
        if (gone) {
            entry.companions.forEach { it.first.delete() }
            entry.record.delete()
        }
        return gone
    }

    /** Deletes for good every entry in [trashDirs] deleted before [cutoff]; how many went. */
    fun purgeOlderThan(trashDirs: List<File>, cutoff: Long): Int =
        list(trashDirs).filter { it.deletedAt < cutoff }.count { purge(it) }

    // ---------- The trash as it was ----------

    /**
     * Carries everything in the old per-folder trashes under [roots] over to
     * the trash of its volume, [trashDirFor] it, keeping when it was deleted,
     * and removes each old trash once it is empty. Names there were
     * `<deleted at>-<name>`, a RAW sharing its photo's time and name. How many
     * entries came over.
     */
    fun migrateLegacy(roots: List<File>, trashDirFor: (File) -> File?, checkCancel: () -> Unit): Int {
        var moved = 0
        for (root in roots) {
            findLegacyDirs(root, checkCancel) { old ->
                val trashDir = trashDirFor(old) ?: return@findLegacyDirs
                val folder = old.parentFile ?: return@findLegacyDirs
                val parsed = old.listFiles().orEmpty()
                    .filter { it.isFile && it.name != NOMEDIA_NAME }
                    .mapNotNull { f -> parseLegacyName(f.name)?.let { (at, name) -> Triple(f, at, name) } }
                val (raws, photos) = parsed.partition { isRaw(File(it.third)) }
                val claimed = mutableSetOf<File>()
                for ((file, at, name) in photos) {
                    val base = File(name).nameWithoutExtension
                    val companions = raws.filter { it.second == at && File(it.third).nameWithoutExtension == base }
                    claimed += companions.map { it.first }
                    val files = listOf(file to File(folder, name)) + companions.map { it.first to File(folder, it.third) }
                    if (store(files, trashDir, at)) moved++
                }
                for ((file, at, name) in raws.filter { it.first !in claimed }) {
                    if (store(listOf(file to File(folder, name)), trashDir, at)) moved++
                }
                val left = old.listFiles().orEmpty()
                if (left.all { it.name == NOMEDIA_NAME }) {
                    left.forEach { it.delete() }
                    old.delete()
                }
            }
        }
        return moved
    }

    /** The time and the original name out of an old trash name; null for one it did not write. */
    internal fun parseLegacyName(name: String): Pair<Long, String>? {
        val dash = name.indexOf('-')
        if (dash <= 0 || dash == name.length - 1) return null
        val at = name.substring(0, dash).takeIf { part -> part.all { it.isDigit() } }?.toLongOrNull() ?: return null
        return at to name.substring(dash + 1)
    }

    private fun findLegacyDirs(dir: File, checkCancel: () -> Unit, onFound: (File) -> Unit) {
        checkCancel()
        for (child in dir.listFiles().orEmpty()) {
            if (!child.isDirectory || Files.isSymbolicLink(child.toPath())) continue
            if (child.name == LEGACY_TRASH_DIR_NAME) onFound(child) else findLegacyDirs(child, checkCancel, onFound)
        }
    }
}
