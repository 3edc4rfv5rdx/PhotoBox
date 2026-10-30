package xx.photobox.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.updater.Updater
import xx.photobox.AppSettings
import xx.photobox.R
import xx.photobox.SLIDE_SECONDS
import xx.photobox.ThemeMode
import xx.photobox.VideoPlayer
import xx.photobox.currentLanguageTag
import xx.photobox.setLanguageTag
import xx.photobox.supportedLanguages

/** Which editor is open; only one can be at a time. */
private enum class Editing { NONE, THEME, ACCENT, LANGUAGE, PLAYER, SLIDE }

/** Theme, accent colour, language, the start-up update check, whether hidden albums are listed, the video player, the slideshow's pace and turning by the sensor. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val themeMode by AppSettings.themeMode.collectAsState()
    val accentIndex by AppSettings.accentIndex.collectAsState()
    val showHidden by AppSettings.showHidden.collectAsState()
    val videoPlayer by AppSettings.videoPlayer.collectAsState()
    val slideSeconds by AppSettings.slideSeconds.collectAsState()
    val sensorRotation by AppSettings.sensorRotation.collectAsState()

    val systemLabel = stringResource(R.string.language_system)
    val languages = remember(context, systemLabel) { supportedLanguages(context, systemLabel) }
    val languageTag = remember(context) { currentLanguageTag(context) }
    val languageLabel = languages.firstOrNull { it.tag == languageTag }?.label ?: systemLabel

    // Saveable: a new theme or language recreates the activity under an open dialog.
    var editing by rememberSaveable { mutableStateOf(Editing.NONE) }
    // The updater keeps this flag in its own preferences file.
    var updateCheck by remember { mutableStateOf(Updater.isEnabled(context)) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
        SwitchRow(stringResource(R.string.setting_sensor_rotation), sensorRotation) {
            AppSettings.setSensorRotation(context, it)
        }
        HorizontalDivider()
        SwitchRow(stringResource(R.string.setting_show_hidden), showHidden) { AppSettings.setShowHidden(context, it) }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_video_player),
            value = stringResource(videoPlayer.labelRes()),
            onClick = { editing = Editing.PLAYER },
        )
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_slide_time),
            value = stringResource(R.string.seconds_short, slideSeconds),
            onClick = { editing = Editing.SLIDE },
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

        Editing.SLIDE -> ChoiceDialog(
            title = stringResource(R.string.setting_slide_time),
            options = SLIDE_SECONDS,
            selected = slideSeconds,
            label = { stringResource(R.string.seconds_short, it) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setSlideSeconds(context, it)
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

private fun VideoPlayer.labelRes(): Int = when (this) {
    VideoPlayer.BUILT_IN -> R.string.player_built_in
    VideoPlayer.EXTERNAL -> R.string.player_external
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
