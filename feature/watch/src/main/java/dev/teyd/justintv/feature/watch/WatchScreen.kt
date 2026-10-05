package dev.teyd.justintv.feature.watch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.designsystem.theme.LocalJustintvDarkTheme

/**
 * Watch one channel: a slot for the video, and chat.
 *
 * The video surface is not here. The playback overlay draws the one PlayerView into this
 * slot, and shrinks that same view into the dock when this screen is popped. Portrait puts
 * the slot on top and chat below. Landscape is fullscreen video; double-tap shows a narrow
 * chat. Back minimises instead of stopping.
 */
@Composable
fun WatchScreen(
    channelLogin: String,
    onMinimize: () -> Unit,
    viewModel: WatchViewModel = activityPlayback(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(onBack = onMinimize)
    LaunchedEffect(channelLogin) { viewModel.open(channelLogin) }
    Immersive(landscape)
    // The slot is black and runs under the status bar, so those icons stay light. The
    // navigation bar follows the app theme, because chat is what sits above it.
    LightStatusBarIcons(statusBarLight = false, navigationBarLight = !LocalJustintvDarkTheme.current)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black),
                )
                if (state.landscapeChat) {
                    ChatPane(modifier = Modifier.width(LandscapeChatWidth).fillMaxHeight())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                    )
                }
                ChatPane(modifier = Modifier.weight(1f).navigationBarsPadding())
            }
        }
    }
}

@Composable
fun activityPlayback(): WatchViewModel {
    val activity = LocalContext.current.findActivity() as? ComponentActivity
        ?: error("Watch screen must be hosted in a ComponentActivity")
    return hiltViewModel(activity)
}

/** Landscape hides the system bars so the video fills the screen. Portrait restores them. */
@Composable
private fun Immersive(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

/**
 * Sets system bar icon contrast for this screen only.
 *
 * Restores the app theme's contrast on the way out, not the device default. A forced dark
 * theme would otherwise flip back to light icons when the system is light.
 */
@Composable
private fun LightStatusBarIcons(statusBarLight: Boolean, navigationBarLight: Boolean) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val appDark = LocalJustintvDarkTheme.current
    DisposableEffect(statusBarLight, navigationBarLight, configuration, appDark) {
        val activity = view.context.findActivity() as? ComponentActivity
        if (activity == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = statusBarLight
            controller.isAppearanceLightNavigationBars = navigationBarLight
            onDispose {
                controller.isAppearanceLightStatusBars = !appDark
                controller.isAppearanceLightNavigationBars = !appDark
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
