package xx.photobox

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import dev.about.About
import dev.about.AboutConfig
import dev.updater.Updater
import dev.updater.UpdaterConfig
import xx.photobox.ui.MainScreen
import xx.photobox.ui.PhotoBoxTheme
import xx.photobox.ui.WindowDark
import xx.photobox.ui.WindowLight
import xx.photobox.ui.isDarkTheme

// One description of this app's release, for the silent check at start-up and
// the About dialog's button alike.
val UPDATER_CONFIG = UpdaterConfig(appKey = "photobox", repo = "PhotoBox")

class MainActivity : ComponentActivity() {

    /** Read again on every resume: the grant is flipped in system Settings, outside the app. */
    private val hasStorageAccess = mutableStateOf(hasAllFilesAccess())

    // The result code from this one is meaningless: the Settings screen for
    // "All files access" does not report back whether the switch was
    // flipped, only that the user left it. onResume is what actually
    // notices the change.
    private val requestAllFilesAccess = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        // Looks for a newer build in this app's own GitHub release and asks
        // before it downloads anything.
        Updater.checkOnStart(this, UPDATER_CONFIG)
        applyWindowTheme()

        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val accentIndex by AppSettings.accentIndex.collectAsState()
            // The window is outside the composition: a theme changed in
            // Settings is carried out to the bars by hand.
            LaunchedEffect(themeMode) { applyWindowTheme() }

            PhotoBoxTheme(themeMode, accentIndex) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        hasStorageAccess = hasStorageAccess.value,
                        onGrantAccess = { requestAllFilesAccess.launch(allFilesAccessIntent(this)) },
                        onExit = ::finish,
                        onAbout = ::showAbout,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasStorageAccess.value = hasAllFilesAccess()
    }

    /**
     * The window background before the first frame and the colour the system
     * bar icons are drawn for, both following the app's theme rather than the
     * system's.
     */
    private fun applyWindowTheme() {
        val systemInDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = isDarkTheme(AppSettings.themeMode.value, systemInDark)
        window.setBackgroundDrawable((if (dark) WindowDark else WindowLight).toArgb().toDrawable())
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun showAbout() {
        About.show(this, AboutConfig(updater = UPDATER_CONFIG, buildDate = BuildConfig.BUILD_DATE))
    }
}
