package dev.teyd.justintv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Stops the screen timing out while a stream is open full-size.
 *
 * The dock does not hold the screen: browsing with a mini player can still sleep. A sleep
 * timer clears this by stopping playback, which leaves the expanded chrome.
 */
@Composable
fun KeepAwake(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
