package xx.photobox

import java.io.File

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
