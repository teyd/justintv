package dev.teyd.justintv

import android.content.BroadcastReceiver
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.teyd.justintv.core.data.ThemeMode
import dev.teyd.justintv.core.designsystem.theme.JustintvTheme
import dev.teyd.justintv.core.player.PlaybackGate
import dev.teyd.justintv.ui.AppearanceViewModel
import dev.teyd.justintv.ui.JustintvApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackGate: PlaybackGate

    private var pipReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance = hiltViewModel<AppearanceViewModel>()
            val mode by appearance.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by appearance.dynamicColor.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // uiMode is handled in place, so the system-bar icon contrast has to follow the
            // chosen palette explicitly. The default detector would keep the device theme.
            val barColor = Color.Transparent.toArgb()
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(barColor, barColor) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(barColor, barColor) { darkTheme },
                )
            }
            JustintvTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                JustintvApp()
            }
        }
        val receiver = Pip.receiver(
            onPlay = { playbackGate.holder?.resume() },
            onPause = { playbackGate.holder?.pause() },
        )
        Pip.register(this, receiver)
        pipReceiver = receiver
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // uiMode is handled in place (the activity is not recreated), so the edge-to-edge
        // styles that give the system bar icons their contrast must be re-applied.
        enableEdgeToEdge()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipHooks.onLeave?.invoke()
    }

    override fun onDestroy() {
        pipReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }
}
