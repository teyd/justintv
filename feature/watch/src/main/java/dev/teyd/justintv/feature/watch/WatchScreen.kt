package dev.teyd.justintv.feature.watch

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.designsystem.theme.JustintvTheme

private val ChatWidth = 240.dp

/**
 * Watch one channel: video and chat.
 *
 * Portrait puts the video on top and chat below. Landscape is fullscreen video; double-tap
 * the picture to show a narrow chat. Back minimises into a corner instead of stopping.
 */
@Composable
fun WatchScreen(
    channelLogin: String,
    onMinimize: () -> Unit,
    viewModel: WatchViewModel = activityPlayback(),
) {
    JustintvTheme(darkTheme = true) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        var chatVisible by rememberSaveable { mutableStateOf(false) }

        BackHandler(onBack = onMinimize)
        LaunchedEffect(channelLogin) { viewModel.open(channelLogin) }
        Immersive(landscape)
        LightStatusBarIcons(light = false)

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (landscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PlayerPane(
                        channel = state.channelLogin.ifBlank { channelLogin },
                        state = state,
                        holder = viewModel.playerHolder,
                        onBack = onMinimize,
                        onTryAnotherSource = viewModel::playAnotherSource,
                        onToggleChat = { chatVisible = !chatVisible },
                        onDoubleTap = { chatVisible = !chatVisible },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    if (chatVisible) {
                        ChatPane(modifier = Modifier.width(ChatWidth).fillMaxHeight())
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // The black box behind the status bar makes the video look edge to edge.
                    Box(modifier = Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding()) {
                        PlayerPane(
                            channel = state.channelLogin,
                            state = state,
                            holder = viewModel.playerHolder,
                            onBack = onMinimize,
                            onTryAnotherSource = viewModel::playAnotherSource,
                            onToggleChat = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }
                    ChatPane(modifier = Modifier.weight(1f).navigationBarsPadding())
                }
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

/** Sets system bar icon contrast for this screen only; the activity's default is re-applied after. */
@Composable
private fun LightStatusBarIcons(light: Boolean) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    DisposableEffect(light, configuration) {
        val activity = view.context.findActivity() as? ComponentActivity
        if (activity == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
            // The activity's auto style follows the system theme, which may have changed
            // while this screen was up; re-applying it is the correct restore.
            onDispose { activity.enableEdgeToEdge() }
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
