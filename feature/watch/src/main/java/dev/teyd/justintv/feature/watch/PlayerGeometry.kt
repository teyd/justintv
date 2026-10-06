package dev.teyd.justintv.feature.watch

import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.PlaybackMethod
import dev.teyd.justintv.core.adfree.PlaylistResolver
import dev.teyd.justintv.core.adfree.ResolvedPlayback
import dev.teyd.justintv.core.data.AdBlockSettingsStore
import dev.teyd.justintv.core.network.DirectorySource
import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.player.ManifestAdDetector
import dev.teyd.justintv.core.player.PlaybackGate
import dev.teyd.justintv.core.player.PlaybackState
import dev.teyd.justintv.core.player.PlayerFactory
import dev.teyd.justintv.core.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Vertical travel (fraction of the dock height) that dismisses the mini player on release. */
internal const val MINI_DISMISS_FRACTION = 0.33f

/** Downward release speed (px/s) that dismisses even if the dock was not dragged far. */
internal const val MINI_DISMISS_VELOCITY = 1_000f

/** True when a vertical release should dismiss: dragged far enough down, or flicked down. */
fun shouldDismissMini(
    offsetY: Float,
    dockHeight: Float,
    velocityY: Float,
): Boolean = offsetY >= dockHeight * MINI_DISMISS_FRACTION || velocityY >= MINI_DISMISS_VELOCITY

/** How far through the collapse (0 expanded, 1 docked, above 1 overscrolled) a release commits. */
internal const val COLLAPSE_COMMIT = 0.45f

/** Release speed (px/s) that flings to the dock or back to the full player. */
internal const val COLLAPSE_FLING = 1_000f

enum class PlayerDragSettle { Expanded, Mini, Dismiss }

/**
 * Adds a finger delta to the collapse. [travel] is the pixel distance from the expanded slot
 * to the dock, so one pixel of finger movement is one pixel of player movement.
 */
fun applyPlayerDrag(
    progress: Float,
    deltaY: Float,
    travel: Float,
): Float {
    if (travel <= 0f) return progress
    return (progress + deltaY / travel).coerceAtLeast(0f)
}

fun settlePlayerDrag(
    progress: Float,
    velocityY: Float,
): PlayerDragSettle =
    when {
        progress >= 1f + MINI_DISMISS_FRACTION -> PlayerDragSettle.Dismiss
        progress >= 1f && velocityY >= MINI_DISMISS_VELOCITY -> PlayerDragSettle.Dismiss
        velocityY <= -COLLAPSE_FLING -> PlayerDragSettle.Expanded
        velocityY >= COLLAPSE_FLING && progress > 0.12f -> PlayerDragSettle.Mini
        progress >= COLLAPSE_COMMIT -> PlayerDragSettle.Mini
        else -> PlayerDragSettle.Expanded
    }

/** A rectangle in the overlay's coordinate space, in pixels. */
data class PlayerFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** The 16:9 slot under the status bar, or the landscape slot beside chat. */
fun expandedPlayerFrame(
    containerWidth: Float,
    containerHeight: Float,
    statusBar: Float,
    landscape: Boolean,
    chatWidth: Float,
): PlayerFrame =
    if (landscape) {
        PlayerFrame(
            left = 0f,
            top = 0f,
            width = (containerWidth - chatWidth).coerceAtLeast(0f),
            height = containerHeight,
        )
    } else {
        val width = containerWidth
        PlayerFrame(
            left = 0f,
            top = statusBar,
            width = width,
            height = width * 9f / 16f,
        )
    }

/** The thumbnail at the left of the bottom dock, sitting above the navigation bar. */
fun dockedVideoFrame(
    containerHeight: Float,
    navigationBar: Float,
    dockHeight: Float,
): PlayerFrame {
    val height = dockHeight.coerceAtLeast(0f)
    return PlayerFrame(
        left = 0f,
        top = containerHeight - navigationBar - height,
        width = height * 16f / 9f,
        height = height,
    )
}

fun lerpFrame(
    from: PlayerFrame,
    to: PlayerFrame,
    fraction: Float,
): PlayerFrame {
    val t = fraction.coerceIn(0f, 1f)
    return PlayerFrame(
        left = lerp(from.left, to.left, t),
        top = lerp(from.top, to.top, t),
        width = lerp(from.width, to.width, t),
        height = lerp(from.height, to.height, t),
    )
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction
