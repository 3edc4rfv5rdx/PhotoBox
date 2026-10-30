package xx.photobox

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.graphics.drawable.toDrawable
import xx.photobox.ui.OpenedFileScreen
import xx.photobox.ui.PhotoBoxTheme

/**
 * "Open with" PhotoBox: a photo or video another app hands over, shown full
 * screen on its own — the gallery's open album and screens are left as they
 * were. Back or ← returns to the app it came from.
 */
class ViewActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: run {
            finish()
            return
        }
        AppSettings.load(this)
        // A viewer is black whatever the theme: so are the window and its bars.
        window.setBackgroundDrawable(Color.BLACK.toDrawable())
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val accentIndex by AppSettings.accentIndex.collectAsState()
            PhotoBoxTheme(themeMode, accentIndex) {
                OpenedFileScreen(uri, onClose = ::finish)
            }
        }
    }
}
