package dev.teyd.justintv.core.player

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Renders a Media3 player surface with live-stream controls.
 *
 * A live Twitch stream has no playlist and no timeline worth scrubbing, so the default
 * transport controls (previous, next, rewind, fast-forward, shuffle, repeat, seek bar,
 * position and duration) are removed. What is left is play/pause on tap.
 *
 * `PlayerView` is used instead of the newer `media3-ui-compose` surface so that buffering
 * indicators and gestures behave predictably on every device.
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
                useController = true
                this.resizeMode = resizeMode
                configureForLiveStream()
                this.player = player.exoPlayer
            }
        },
        update = { view -> view.player = player.exoPlayer },
        onRelease = { view -> view.player = null },
    )
}

private fun PlayerView.configureForLiveStream() {
    setShowPreviousButton(false)
    setShowNextButton(false)
    setShowRewindButton(false)
    setShowFastForwardButton(false)
    setShowShuffleButton(false)
    setShowSubtitleButton(false)
    setShowVrButton(false)
    setRepeatToggleModes(0)
    setControllerShowTimeoutMs(CONTROLS_TIMEOUT_MS)
    setControllerHideOnTouch(true)

    // Hide the seek bar and time labels. They are part of the controller layout, not a flag.
    listOf(
        androidx.media3.ui.R.id.exo_progress,
        androidx.media3.ui.R.id.exo_position,
        androidx.media3.ui.R.id.exo_duration,
        androidx.media3.ui.R.id.exo_time,
    ).forEach { id -> findViewById<View?>(id)?.visibility = View.GONE }
}

private const val CONTROLS_TIMEOUT_MS = 2_500
