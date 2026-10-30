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

/**
 * A new name in place of [current] — a file's without its extension, which
 * stays as [suffix], or an album's. Rename stays off until [check] says the
 * name would work; [onRename] is handed the name and what to do should the
 * rename fail.
 */
@Composable
fun RenameDialog(
    current: String,
    suffix: String?,
    check: (String) -> NameCheck,
    onRename: (String, onFailed: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(current) { mutableStateOf(current) }
    var failed by remember { mutableStateOf(false) }
    val nameCheck = check(name)
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
                check = nameCheck,
                failure = if (failed) stringResource(R.string.rename_failed) else null,
                suffix = suffix,
            )
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.rename), enabled = nameCheck == NameCheck.OK) {
                onRename(name) { failed = true }
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}
