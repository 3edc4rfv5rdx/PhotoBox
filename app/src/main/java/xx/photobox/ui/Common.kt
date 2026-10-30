package xx.photobox.ui

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xx.photobox.ACCENT_COUNT
import xx.photobox.R
import xx.photobox.ThemeMode
import java.text.NumberFormat

// ---------- Theme ----------

// The window is a distinct tone from the containers, so dialogs and cards stand
// out against the screen behind them. The same values as in the other apps
// built on this skeleton.
val WindowLight = Color(0xFFF1F2F4)
val WindowDark = Color(0xFF121212)
private val TonalButtonDark = Color(0xFF5A5A5A)

// Material3's baseline `error` is a soft, low-chroma rose in the dark scheme
// (meant for text on a dark surface, not a button fill) — nowhere near as
// loud as the trash button needs to read. Same red as the accent palette's
// own "red" choice, so it stays a proper red in both themes.
private val ErrorRed = Color(0xFFE53935)

private val LightColors = lightColorScheme(
    background = WindowLight,
    surface = WindowLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    background = WindowDark,
    surface = WindowDark,
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF1F1F1F),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF303030),
    // Tonal buttons must read as buttons against the dark window.
    secondaryContainer = TonalButtonDark,
    onSecondaryContainer = Color.White,
    error = ErrorRed,
    onError = Color.White,
)

/**
 * Accent choices, the same six as in the other apps built on this skeleton:
 * mid-tones that hold contrast on both windows and carry white text as a
 * button fill.
 */
val AccentPalette = listOf(
    Color(0xFF00897B), // teal
    Color(0xFF1E88E5), // blue
    Color(0xFF5C6BC0), // indigo
    Color(0xFF8E24AA), // purple
    Color(0xFFEF6C00), // orange
    Color(0xFFE53935), // red
).also { check(it.size == ACCENT_COUNT) }

fun accentAt(index: Int): Color = AccentPalette[index.coerceIn(AccentPalette.indices)]

/** Free of Compose, so the activity can ask it before the first frame. */
fun isDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PhotoBoxTheme(themeMode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode, isSystemInDarkTheme())
    val scheme = (if (dark) DarkColors else LightColors).copy(
        primary = accentAt(accentIndex),
        onPrimary = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Text ----------

/** A file size the way the system's own screens write it. */
fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

/** A count in the device's own digit grouping. */
fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)

// ---------- Small controls ----------

/** An icon button a size up from Material's 40dp: the full 48dp it takes up for touch anyway, so a row of them is
 * no wider for it — with a glyph to match. */
val LARGE_BUTTON = 48.dp
val LARGE_ICON = 28.dp

/** Every icon button in a top bar: [LARGE_BUTTON] with a [LARGE_ICON] glyph. Without a
 * [tint] the glyph takes the content colour, which also dims it while disabled. */
@Composable
fun LargeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(LARGE_BUTTON)) {
        if (tint == null) {
            Icon(icon, contentDescription, Modifier.size(LARGE_ICON))
        } else {
            Icon(icon, contentDescription, Modifier.size(LARGE_ICON), tint = tint)
        }
    }
}

/** The ⋮ button that opens an [AppMenu]. */
@Composable
fun MoreButton(onClick: () -> Unit, enabled: Boolean = true) {
    LargeIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), onClick, enabled)
}

// ---------- Dialog pieces ----------

private val DIALOG_BUTTON_PADDING = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = DIALOG_BUTTON_PADDING) {
        Text(text, maxLines = 1)
    }
}

/**
 * Every drop-down menu in the app: a raised card with an outline, so it
 * stands off the screen behind it in both themes.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = scheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, scheme.outline),
        content = content,
    )
}

/** One line of an [AppMenu], in the size every menu here uses. */
@Composable
fun AppMenuItem(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.titleLarge) },
        enabled = enabled,
        onClick = onClick,
    )
}

/** Single choice from a labelled list; scrolls, since the language list can grow. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onPick(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** One accent colour, ringed when it is the one in force. */
@Composable
fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

// ---------- Screens ----------

/** A screen filling the window, with a back button and title above whatever [content] puts in its Column — used
 * for every screen the ⋮ menu opens, so they navigate like the rest of the app instead of popping up as a dialog.
 * [actions] sits at the title row's trailing end, for a screen-specific button. */
@Composable
fun AppScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

/**
 * Work under way, in the middle of [modifier]'s space: a bar when its
 * [progress] is known, a spinner when not, and [text] under either.
 */
@Composable
fun ProgressState(text: String?, modifier: Modifier = Modifier.fillMaxSize(), progress: (() -> Float)? = null) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (progress != null) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator()
        }
        if (text != null) Text(text, Modifier.padding(top = 16.dp))
    }
}

/** A screen with nothing to show yet: [icon] above the [title], [action] below. */
@Composable
fun EmptyState(
    icon: ImageVector?,
    title: String,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}
