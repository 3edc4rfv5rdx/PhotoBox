package xx.photobox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import xx.photobox.NameCheck
import xx.photobox.R
import xx.photobox.checkRename
import java.io.File

/**
 * A new name for [file], typed without its extension, which stays. Rename
 * stays off until the name would work; [onRename] is handed the name and
 * what to do should the rename fail.
 */
@Composable
fun RenameDialog(file: File, onRename: (String, onFailed: () -> Unit) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(file.path) { mutableStateOf(file.nameWithoutExtension) }
    var failed by remember { mutableStateOf(false) }
    val check = checkRename(file, name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            NameField(
                name = name,
                onName = {
                    name = it
                    failed = false
                },
                check = check,
                failure = if (failed) stringResource(R.string.rename_failed) else null,
                suffix = file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" },
            )
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.rename), enabled = check == NameCheck.OK) {
                onRename(name) { failed = true }
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}
