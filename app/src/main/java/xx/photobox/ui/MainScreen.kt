package xx.photobox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.photobox.R

/** The screens besides the album's thumbnails, each opened from the ⋮ menu or the title. */
private enum class Screen { THUMBNAILS, ALBUMS, TRASH, SETTINGS }

/**
 * The whole app: the open album's thumbnails, with ✕ to leave on the left, the
 * album's name in the middle opening the album list, and ⋮ on the right for
 * everything else.
 */
@Composable
fun MainScreen(
    hasStorageAccess: Boolean,
    onGrantAccess: () -> Unit,
    onExit: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable: picking a language recreates the activity, and a plain
    // remember would drop the user back on the thumbnails mid-Settings.
    var screen by rememberSaveable { mutableStateOf(Screen.THUMBNAILS) }
    var menuOpen by remember { mutableStateOf(false) }
    val backToThumbnails = { screen = Screen.THUMBNAILS }

    Box(modifier.fillMaxSize()) {
        when (screen) {
            Screen.ALBUMS -> AppScreen(title = stringResource(R.string.albums), onBack = backToThumbnails) {
                EmptyState(icon = null, title = stringResource(R.string.not_ready))
            }

            Screen.TRASH -> AppScreen(title = stringResource(R.string.trash), onBack = backToThumbnails) {
                EmptyState(icon = null, title = stringResource(R.string.not_ready))
            }

            Screen.SETTINGS -> AppScreen(
                title = stringResource(R.string.settings),
                onBack = backToThumbnails,
                actions = {
                    IconButton(onClick = onAbout, modifier = Modifier.size(LARGE_BUTTON)) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.about), Modifier.size(LARGE_ICON))
                    }
                },
            ) {
                SettingsScreen(modifier = Modifier.weight(1f))
            }

            Screen.THUMBNAILS -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExit, modifier = Modifier.size(LARGE_BUTTON)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.exit), Modifier.size(LARGE_ICON))
                    }
                    // The album's name once albums exist; tapping it is the quick way to another one.
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = hasStorageAccess) { screen = Screen.ALBUMS }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                    Box {
                        MoreButton(onClick = { menuOpen = true })
                        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuItem(stringResource(R.string.albums), enabled = hasStorageAccess) {
                                menuOpen = false
                                screen = Screen.ALBUMS
                            }
                            AppMenuItem(stringResource(R.string.trash), enabled = hasStorageAccess) {
                                menuOpen = false
                                screen = Screen.TRASH
                            }
                            // Runs through the open album; there is none to run through yet.
                            AppMenuItem(stringResource(R.string.slideshow), enabled = false) {
                                menuOpen = false
                            }
                            AppMenuItem(stringResource(R.string.settings)) {
                                menuOpen = false
                                screen = Screen.SETTINGS
                            }
                        }
                    }
                }
                if (hasStorageAccess) {
                    EmptyState(icon = null, title = stringResource(R.string.not_ready))
                } else {
                    EmptyState(
                        icon = null,
                        title = stringResource(R.string.need_storage_access),
                        message = stringResource(R.string.need_storage_access_hint),
                        action = { Button(onClick = onGrantAccess) { Text(stringResource(R.string.grant_access)) } },
                    )
                }
            }
        }
    }
}
