package dev.teyd.justintv.core.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Renders the bare video surface.
 *
 * The built-in controller is turned off: a live stream has no timeline to scrub, and the app
 * draws its own minimal controls and stats on top (see the watch screen).
 *
 * `PlayerView` is used instead of the newer `media3-ui-compose` surface so that buffering
 * and rendering behave predictably on every device.
 */
@Composable
fun VideoPlayer(
    player: PlayerHolder,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                this.resizeMode = resizeMode
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player.exoPlayer
            }
        },
        update = { view -> view.player = player.exoPlayer },
        onRelease = { view -> view.player = null },
    )
}
