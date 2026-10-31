package xx.photobox.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.updater.Updater
import xx.photobox.AppSettings
import xx.photobox.MediaRotation
import xx.photobox.R
import xx.photobox.SLIDE_SECONDS
import xx.photobox.GRID_COLUMNS
import xx.photobox.TRASH_DAYS
import xx.photobox.ThemeMode
import xx.photobox.VideoPlayer
import xx.photobox.currentLanguageTag
import xx.photobox.setLanguageTag
import xx.photobox.supportedLanguages
import kotlin.math.roundToInt

/** Which editor is open; only one can be at a time. */
private enum class Editing { NONE, THEME, ACCENT, LANGUAGE, PLAYER, ROTATION, COLUMNS, TRASH }

/**
 * Theme, accent colour, language, the start-up update check, whether hidden
 * albums are listed, the grid's columns — a pinch sets them too, but not from
 * a TV remote — the video player, the slideshow's pace, when photos turn and
 * how long the trash keeps a file.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val themeMode by AppSettings.themeMode.collectAsState()
    val accentIndex by AppSettings.accentIndex.collectAsState()
    val showHidden by AppSettings.showHidden.collectAsState()
    val videoPlayer by AppSettings.videoPlayer.collectAsState()
    val slideSeconds by AppSettings.slideSeconds.collectAsState()
    val mediaRotation by AppSettings.mediaRotation.collectAsState()
    val trashDays by AppSettings.trashDays.collectAsState()
    val gridColumns by AppSettings.gridColumns.collectAsState()

    val systemLabel = stringResource(R.string.language_system)
    val languages = remember(context, systemLabel) { supportedLanguages(context, systemLabel) }
    val languageTag = remember(context) { currentLanguageTag(context) }
    val languageLabel = languages.firstOrNull { it.tag == languageTag }?.label ?: systemLabel

    // Saveable: a new theme or language recreates the activity under an open dialog.
    var editing by rememberSaveable { mutableStateOf(Editing.NONE) }
    // The updater keeps this flag in its own preferences file.
    var updateCheck by remember { mutableStateOf(Updater.isEnabled(context)) }

    Column(
        modifier = modifier.fillMaxSize().verticalScrollByAny().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingRow(
            label = stringResource(R.string.setting_theme),
            value = stringResource(themeMode.labelRes()),
            onClick = { editing = Editing.THEME },
        )
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = Editing.ACCENT }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_accent),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            AccentSwatch(accentAt(accentIndex), selected = false, onClick = { editing = Editing.ACCENT })
        }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_language),
            value = languageLabel,
            onClick = { editing = Editing.LANGUAGE },
        )
        HorizontalDivider()
        SwitchRow(stringResource(R.string.setting_update_check), updateCheck) {
            updateCheck = it
            Updater.setEnabled(context, it)
        }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_media_rotation),
            value = stringResource(mediaRotation.labelRes()),
            onClick = { editing = Editing.ROTATION },
        )
        HorizontalDivider()
        SwitchRow(stringResource(R.string.setting_show_hidden), showHidden) { AppSettings.setShowHidden(context, it) }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_grid_columns),
            value = gridColumns.toString(),
            onClick = { editing = Editing.COLUMNS },
        )
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_video_player),
            value = stringResource(videoPlayer.labelRes()),
            onClick = { editing = Editing.PLAYER },
        )
        HorizontalDivider()
        SlideTimeRow(slideSeconds) { AppSettings.setSlideSeconds(context, it) }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_trash_days),
            value = pluralStringResource(R.plurals.days, trashDays, trashDays),
            onClick = { editing = Editing.TRASH },
        )
    }

    when (editing) {
        Editing.THEME -> ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setThemeMode(context, it)
                editing = Editing.NONE
            },
        )

        Editing.ACCENT -> AlertDialog(
            onDismissRequest = { editing = Editing.NONE },
            title = { Text(stringResource(R.string.setting_accent)) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AccentPalette.forEachIndexed { index, color ->
                        AccentSwatch(color, selected = index == accentIndex, onClick = {
                            AppSettings.setAccentIndex(context, index)
                            editing = Editing.NONE
                        })
                    }
                }
            },
            confirmButton = { DialogDismissButton(stringResource(R.string.cancel)) { editing = Editing.NONE } },
        )

        Editing.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = languages,
            selected = languages.firstOrNull { it.tag == languageTag },
            label = { it.label },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                editing = Editing.NONE
                // Persisted, and the activity recreated in the new language.
                activity?.let { a -> setLanguageTag(a, it.tag) }
            },
        )

        Editing.PLAYER -> ChoiceDialog(
            title = stringResource(R.string.setting_video_player),
            options = VideoPlayer.entries,
            selected = videoPlayer,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setVideoPlayer(context, it)
                editing = Editing.NONE
            },
        )

        Editing.ROTATION -> ChoiceDialog(
            title = stringResource(R.string.setting_media_rotation),
            options = MediaRotation.entries,
            selected = mediaRotation,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setMediaRotation(context, it)
                editing = Editing.NONE
            },
        )

        Editing.COLUMNS -> ChoiceDialog(
            title = stringResource(R.string.setting_grid_columns),
            options = GRID_COLUMNS.toList(),
            selected = gridColumns,
            label = { it.toString() },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setGridColumns(context, it)
                editing = Editing.NONE
            },
        )

        Editing.TRASH -> ChoiceDialog(
            title = stringResource(R.string.setting_trash_days),
            options = TRASH_DAYS,
            selected = trashDays,
            label = { pluralStringResource(R.plurals.days, it, it) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setTrashDays(context, it)
                editing = Editing.NONE
            },
        )

        Editing.NONE -> Unit
    }
}

/** A setting that is on or off. The whole row is not clickable: the switch is the control. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The slideshow's time per photo, a second at a time across [SLIDE_SECONDS].
 * The number follows the thumb; it is kept once the thumb is let go.
 */
@Composable
private fun SlideTimeRow(seconds: Int, onChange: (Int) -> Unit) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged?.roundToInt() ?: seconds
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.setting_slide_time),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.seconds_short, shown),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = dragged ?: seconds.toFloat(),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { onChange(it.roundToInt()) }
                dragged = null
            },
            valueRange = SLIDE_SECONDS.first.toFloat()..SLIDE_SECONDS.last.toFloat(),
            // Stops between the ends, so the thumb lands on whole seconds.
            steps = SLIDE_SECONDS.last - SLIDE_SECONDS.first - 1,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun MediaRotation.labelRes(): Int = when (this) {
    MediaRotation.SENSOR -> R.string.rotation_sensor
    MediaRotation.SYSTEM -> R.string.rotation_system
    MediaRotation.NEVER -> R.string.rotation_never
}

private fun VideoPlayer.labelRes(): Int = when (this) {
    VideoPlayer.BUILT_IN -> R.string.player_built_in
    VideoPlayer.EXTERNAL -> R.string.player_external
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
