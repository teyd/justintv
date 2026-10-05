package dev.teyd.justintv.feature.watch

import android.app.Activity
import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.designsystem.theme.JustintvTheme

private val ChatWidth = 340.dp

/**
 * Watch one channel: video and chat.
 *
 * Portrait puts the video on top and chat below. Landscape puts them side by side, and the
 * chat can be hidden for a full-width picture. The screen is always dark; it is a video app.
 */
@Composable
fun WatchScreen(
    onBack: () -> Unit,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    JustintvTheme(darkTheme = true) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        var chatVisible by rememberSaveable { mutableStateOf(true) }

        LightStatusBarIcons(light = false)

        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.playerHolder.pause() }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            if (state.method.isNotEmpty()) viewModel.playerHolder.resume()
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (landscape) {
                Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    PlayerPane(
                        channel = state.channelLogin,
                        state = state,
                        holder = viewModel.playerHolder,
                        onBack = onBack,
                        onTryAnotherSource = viewModel::playAnotherSource,
                        onToggleChat = { chatVisible = !chatVisible },
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
                            onBack = onBack,
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

/** Sets status bar icon contrast for this screen only, restoring the previous setting after. */
@Composable
private fun LightStatusBarIcons(light: Boolean) {
    val view = LocalView.current
    DisposableEffect(light) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = light
            onDispose { controller.isAppearanceLightStatusBars = previous }
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
