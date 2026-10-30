package xx.photobox

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Light/dark override; SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How many accents the palette offers; the colours themselves are in ui/Common.kt. */
const val ACCENT_COUNT = 6

/** Where a video plays: in the app's own player, or handed to another app. */
enum class VideoPlayer { BUILT_IN, EXTERNAL }

/**
 * When photos, videos and the slideshow turn with the phone: by the sensor
 * whatever the system says, only while the system's auto-rotate is on, or not
 * at all.
 */
enum class MediaRotation { SENSOR, SYSTEM, NEVER }

/** The times a slideshow can hold each photo for, in seconds. */
val SLIDE_SECONDS = 1..30
const val DEFAULT_SLIDE_SECONDS = 5

/** How many thumbnails across a pinch can set. */
val GRID_COLUMNS = 2..5
const val DEFAULT_GRID_COLUMNS = 3

/**
 * Process-wide settings, backed by SharedPreferences and exposed as [StateFlow]
 * for Compose. Call [load] once before the UI reads them.
 */
object AppSettings {
    private const val PREFS = "photobox"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ACCENT = "accent_index"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SHOW_HIDDEN = "show_hidden"
    private const val KEY_ALBUM_ORDER = "album_order"
    private const val KEY_ALBUM_PATH = "album_path"
    private const val KEY_GRID_COLUMNS = "grid_columns"
    private const val KEY_ITEM_ORDER = "item_order"
    private const val KEY_VIDEO_PLAYER = "video_player"
    private const val KEY_SLIDE_SECONDS = "slide_seconds"
    private const val KEY_MEDIA_ROTATION = "media_rotation"

    /** The on/off switch [KEY_MEDIA_ROTATION] replaced; read once to carry an "off" over. */
    private const val KEY_SENSOR_ROTATION_OLD = "sensor_rotation"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(0)
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    /** Whether albums under a dot-folder or a .nomedia file are listed. */
    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    private val _albumOrder = MutableStateFlow(AlbumOrder.NAME)
    val albumOrder: StateFlow<AlbumOrder> = _albumOrder.asStateFlow()

    /** Thumbnails across the screen, changed with a pinch. */
    private val _gridColumns = MutableStateFlow(DEFAULT_GRID_COLUMNS)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _itemOrder = MutableStateFlow(ItemOrder.DATE)
    val itemOrder: StateFlow<ItemOrder> = _itemOrder.asStateFlow()

    private val _videoPlayer = MutableStateFlow(VideoPlayer.BUILT_IN)
    val videoPlayer: StateFlow<VideoPlayer> = _videoPlayer.asStateFlow()

    /** How long each photo of a slideshow stays up, in [SLIDE_SECONDS]. */
    private val _slideSeconds = MutableStateFlow(DEFAULT_SLIDE_SECONDS)
    val slideSeconds: StateFlow<Int> = _slideSeconds.asStateFlow()

    /** When photos, videos and the slideshow turn with the phone; the rest of the app never does. */
    private val _mediaRotation = MutableStateFlow(MediaRotation.SENSOR)
    val mediaRotation: StateFlow<MediaRotation> = _mediaRotation.asStateFlow()

    fun load(context: Context) {
        val prefs = prefs(context)
        _themeMode.value = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM)
        _accentIndex.value = prefs.getInt(KEY_ACCENT, 0).coerceIn(0, ACCENT_COUNT - 1)
        _showHidden.value = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        _albumOrder.value = enumOr(prefs.getString(KEY_ALBUM_ORDER, null), AlbumOrder.NAME)
        _gridColumns.value = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS).coerceIn(GRID_COLUMNS)
        _itemOrder.value = enumOr(prefs.getString(KEY_ITEM_ORDER, null), ItemOrder.DATE)
        _videoPlayer.value = enumOr(prefs.getString(KEY_VIDEO_PLAYER, null), VideoPlayer.BUILT_IN)
        _slideSeconds.value = prefs.getInt(KEY_SLIDE_SECONDS, DEFAULT_SLIDE_SECONDS).takeIf { it in SLIDE_SECONDS }
            ?: DEFAULT_SLIDE_SECONDS
        val oldOff = !prefs.getBoolean(KEY_SENSOR_ROTATION_OLD, true)
        _mediaRotation.value = enumOr(
            prefs.getString(KEY_MEDIA_ROTATION, null),
            if (oldOff) MediaRotation.NEVER else MediaRotation.SENSOR,
        )
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        prefs(context).edit { putString(KEY_THEME, mode.name) }
    }

    fun setAccentIndex(context: Context, index: Int) {
        val clamped = index.coerceIn(0, ACCENT_COUNT - 1)
        _accentIndex.value = clamped
        prefs(context).edit { putInt(KEY_ACCENT, clamped) }
    }

    fun setShowHidden(context: Context, enabled: Boolean) {
        _showHidden.value = enabled
        prefs(context).edit { putBoolean(KEY_SHOW_HIDDEN, enabled) }
    }

    fun setAlbumOrder(context: Context, order: AlbumOrder) {
        _albumOrder.value = order
        prefs(context).edit { putString(KEY_ALBUM_ORDER, order.name) }
    }

    fun setGridColumns(context: Context, columns: Int) {
        val clamped = columns.coerceIn(GRID_COLUMNS)
        _gridColumns.value = clamped
        prefs(context).edit { putInt(KEY_GRID_COLUMNS, clamped) }
    }

    fun setItemOrder(context: Context, order: ItemOrder) {
        _itemOrder.value = order
        prefs(context).edit { putString(KEY_ITEM_ORDER, order.name) }
    }

    fun setVideoPlayer(context: Context, player: VideoPlayer) {
        _videoPlayer.value = player
        prefs(context).edit { putString(KEY_VIDEO_PLAYER, player.name) }
    }

    fun setSlideSeconds(context: Context, seconds: Int) {
        if (seconds !in SLIDE_SECONDS) return
        _slideSeconds.value = seconds
        prefs(context).edit { putInt(KEY_SLIDE_SECONDS, seconds) }
    }

    fun setMediaRotation(context: Context, rotation: MediaRotation) {
        _mediaRotation.value = rotation
        prefs(context).edit {
            putString(KEY_MEDIA_ROTATION, rotation.name)
            remove(KEY_SENSOR_ROTATION_OLD)
        }
    }

    /** The album open last, kept across launches. Null before one was ever opened. */
    fun albumPath(context: Context): String? = prefs(context).getString(KEY_ALBUM_PATH, null)

    fun setAlbumPath(context: Context, path: String) {
        prefs(context).edit { putString(KEY_ALBUM_PATH, path) }
    }

    /**
     * The per-app language below API 33, where the platform does not keep one.
     * Read straight from the file: it is needed while the activity attaches,
     * before [load].
     */
    fun languageTag(context: Context): String = prefs(context).getString(KEY_LANGUAGE, null).orEmpty()

    fun setLanguageTag(context: Context, tag: String) {
        prefs(context).edit { putString(KEY_LANGUAGE, tag) }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ---------- Language ----------

/** One entry of the language picker; [tag] empty means "follow the device". */
data class LanguageOption(val tag: String, val label: String)

/**
 * The languages this build ships, read from res/xml/locales_config.xml, each
 * named in itself. Read from the XML directly: LocaleConfig, which would do it,
 * needs API 34.
 */
fun supportedLanguages(context: Context, systemLabel: String): List<LanguageOption> {
    val tags = mutableListOf<String>()
    context.resources.getXml(R.xml.locales_config).use { parser ->
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
            }
        }
    }
    val languages = tags.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        LanguageOption(
            tag = tag,
            label = locale.getDisplayLanguage(locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
        )
    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    return listOf(LanguageOption("", systemLabel)) + languages
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * The per-app language; empty for "follow the device". The platform holds it
 * from API 33, [AppSettings] below that.
 */
fun currentLanguageTag(context: Context): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            .applicationLocales
            .takeUnless { it.isEmpty }
            ?.get(0)
            ?.toLanguageTag()
            .orEmpty()
    } else {
        AppSettings.languageTag(context)
    }

/**
 * From API 33 the system persists the choice and recreates the activity;
 * below that the app does both, and [localizedContext] applies it.
 */
fun setLanguageTag(activity: Activity, tag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    } else {
        AppSettings.setLanguageTag(activity, tag)
        activity.recreate()
    }
}

/**
 * The activity's base context in the chosen language, below API 33; from 33
 * the platform does this itself and [base] comes back as it is.
 */
fun localizedContext(base: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
    val tag = AppSettings.languageTag(base)
    val locale = if (tag.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    if (tag.isEmpty()) return base
    // Only the locale is set: an empty Configuration overrides nothing else, so
    // orientation and screen size still follow the device.
    val override = Configuration().apply { setLocale(locale) }
    return base.createConfigurationContext(override)
}
