package xx.photobox.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xx.photobox.FileInfo
import xx.photobox.R
import xx.photobox.exposureText
import xx.photobox.readFileInfo
import xx.photobox.trimmedNumber
import java.io.File
import java.util.Locale

/**
 * What there is to know about [file]: its name and folder — those of
 * [shownAs], where it is known by another, as a trashed file by where it came
 * from — size, picture
 * size, when it was taken and changed, the camera and its settings, and the
 * place, which opens in a map app when tapped. Lines the file says nothing
 * about are left out.
 */
@Composable
fun InfoDialog(file: File, onDismiss: () -> Unit, shownAs: File = file) {
    val context = LocalContext.current
    val info by produceState<FileInfo?>(initialValue = null, key1 = file) {
        value = withContext(Dispatchers.IO) { readFileInfo(file) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info)) },
        text = {
            val i = info
            if (i == null) {
                ProgressState(text = null, modifier = Modifier.fillMaxWidth())
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InfoLine(stringResource(R.string.info_name), shownAs.name)
                    InfoLine(stringResource(R.string.info_folder), shownAs.parent.orEmpty())
                    InfoLine(stringResource(R.string.info_size), formatSize(context, i.size))
                    if (i.width != null && i.height != null) {
                        InfoLine(stringResource(R.string.info_resolution), "${i.width} × ${i.height}")
                    }
                    i.durationMs?.let { InfoLine(stringResource(R.string.info_duration), formatClock(it)) }
                    i.taken?.let { InfoLine(stringResource(R.string.info_taken), formatDateTime(context, it)) }
                    InfoLine(stringResource(R.string.info_modified), formatDateTime(context, i.modified))
                    i.camera?.let { InfoLine(stringResource(R.string.info_camera), it) }
                    exposureLine(i)?.let { InfoLine(stringResource(R.string.info_exposure), it) }
                    if (i.latitude != null && i.longitude != null) {
                        val place = String.format(Locale.ROOT, "%.5f, %.5f", i.latitude, i.longitude)
                        InfoLine(stringResource(R.string.info_location), place, link = true) {
                            openMap(context, i.latitude, i.longitude)
                        }
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.close), onDismiss) },
    )
}

@Composable
private fun InfoLine(label: String, value: String, link: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (link) TextDecoration.Underline else null,
        )
    }
}

/** The camera's settings on one line — f/1.8 · 1/120 s · ISO 100 · 4.2 mm — or null when it recorded none. */
@Composable
private fun exposureLine(info: FileInfo): String? {
    val parts = listOfNotNull(
        info.fNumber?.let { "f/${trimmedNumber(it)}" },
        info.exposureSeconds?.let { stringResource(R.string.unit_seconds, exposureText(it)) },
        info.iso?.let { "ISO $it" },
        info.focalLengthMm?.let { stringResource(R.string.unit_millimetres, trimmedNumber(it)) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

/** The place in whatever map app the phone has, with a pin on it. */
private fun openMap(context: Context, latitude: Double, longitude: Double) {
    val point = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "geo:$point?q=$point".toUri()))
    } catch (e: ActivityNotFoundException) {
        // No map app: the numbers are on screen to copy by hand.
    }
}
