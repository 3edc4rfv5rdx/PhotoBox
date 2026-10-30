package xx.photobox.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.ItemFilter
import xx.photobox.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val DATE_LABEL_WIDTH = 48.dp

/** The filter being set: the part of the name to look for, and the first and last day. OK hands it to [onApply]. */
@Composable
fun FilterDialog(filter: ItemFilter, onApply: (ItemFilter) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(filter.name) }
    var from by rememberSaveable { mutableStateOf(filter.from) }
    var to by rememberSaveable { mutableStateOf(filter.to) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.filter_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(stringResource(R.string.filter_from), from) { from = it }
                DateField(stringResource(R.string.filter_to), to) { to = it }
            }
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.ok)) { onApply(ItemFilter(name.trim(), from, to)) }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** One end of the date range: [label], the day or "any day", which opens a calendar, and ✕ to take the day off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate?, onDate: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(DATE_LABEL_WIDTH))
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Text(
                date?.let { formatDay(context, it) } ?: stringResource(R.string.filter_any_date),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (date != null) {
            IconButton(onClick = { onDate(null) }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
        }
    }
    if (picking) {
        // The calendar counts in UTC midnights, whatever the phone's zone.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                DialogConfirmButton(stringResource(R.string.ok)) {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }
            },
            dismissButton = { DialogDismissButton(stringResource(R.string.cancel)) { picking = false } },
        ) {
            DatePicker(state)
        }
    }
}

/**
 * The line under the top bar while [filter] is on: what it looks for, and
 * [shown] of [total] files. A tap opens it to change, ✕ takes it off.
 */
@Composable
fun FilterBar(filter: ItemFilter, shown: Int, total: Int, onEdit: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val days = when {
        filter.from != null && filter.to != null -> "${formatDay(context, filter.from)} – ${formatDay(context, filter.to)}"
        filter.from != null -> "${formatDay(context, filter.from)} –"
        filter.to != null -> "– ${formatDay(context, filter.to)}"
        else -> null
    }
    val parts = listOfNotNull(
        filter.name.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
        days,
        stringResource(R.string.filter_count, formatCount(shown), formatCount(total)),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.filter_off)) }
    }
}

/** [date] in the phone's own short numeric way, with the year. */
private fun formatDay(context: Context, date: LocalDate): String = DateUtils.formatDateTime(
    context,
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_NUMERIC_DATE,
)
