package dev.teyd.justintv.core.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Renders a Media3 player surface.
 *
 * `PlayerView` is used instead of the newer `media3-ui-compose` surface so that controls,
 * buffering indicators and gestures behave predictably on every device.
 */
@Composable
fun VideoPlayer(
    player: PlayerHolder,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.useController = useController
                this.resizeMode = resizeMode
                this.player = player.exoPlayer
            }
        },
        update = { view ->
            view.player = player.exoPlayer
            view.useController = useController
        },
        onRelease = { view -> view.player = null },
    )
}
