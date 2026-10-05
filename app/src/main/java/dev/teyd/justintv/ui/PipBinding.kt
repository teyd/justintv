package dev.teyd.justintv.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.Pip
import dev.teyd.justintv.PipHooks
import dev.teyd.justintv.core.data.PlaybackSettingsStore
import dev.teyd.justintv.feature.watch.PlayerChrome
import dev.teyd.justintv.feature.watch.WatchViewModel

@Composable
fun rememberInPip(): Boolean {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Keeps system PiP params in sync, and enters PiP when the user leaves if the setting is on. */
@Composable
fun PipBinding(
    playback: WatchViewModel,
    settings: PlaybackSettingsStore,
) {
    val activity = LocalContext.current.findActivity() as? Activity ?: return
    val pipOn by settings.pictureInPicture.collectAsStateWithLifecycle(initialValue = true)
    val playing by playback.playerHolder.playback.collectAsStateWithLifecycle()
    val chrome by playback.chrome.collectAsStateWithLifecycle()
    val active = chrome != PlayerChrome.Hidden && playing.isPlaying && pipOn

    DisposableEffect(activity, active, playing.isPlaying) {
        if (Pip.supported(activity)) {
            activity.setPictureInPictureParams(Pip.params(activity, playing.isPlaying, autoEnter = active))
        }
        PipHooks.onLeave = {
            if (active) Pip.enter(activity, isPlaying = true)
        }
        onDispose { PipHooks.onLeave = null }
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
