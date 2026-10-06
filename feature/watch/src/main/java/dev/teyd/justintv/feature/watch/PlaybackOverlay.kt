package dev.teyd.justintv.feature.watch

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.teyd.justintv.core.player.VideoPlayer
import kotlin.math.abs
import kotlin.math.roundToInt

/** Height of the docked bar, not including the navigation bar. Lists should pad by this. */
val PlaybackDockHeight = 64.dp

internal val LandscapeChatWidth = 240.dp

private const val APPEAR_MS = 180
private const val PREVIEW_FADE_MS = 260

private val DockSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

/**
 * The only video surface in the app.
 *
 * Expanded, it sits in the watch slot and the controls draw on top of it. Minimised, the same
 * view shrinks into the left of a bottom dock: title and channel beside it, play and close
 * off the picture. Drag the dock down to dismiss. Tap it to open the watch screen again.
 */
@Composable
fun PlaybackOverlay(
    viewModel: WatchViewModel,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome by viewModel.chrome.collectAsStateWithLifecycle()
    val watchState by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playerHolder.playback.collectAsStateWithLifecycle()
    val sleepEndsAt by viewModel.sleepEndsAt.collectAsStateWithLifecycle()
    if (chrome == PlayerChrome.Hidden || watchState.channelLogin.isBlank()) return

    val mini = chrome == PlayerChrome.Mini
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    val navigationBar = WindowInsets.navigationBars.getBottom(density).toFloat()
    val dockHeightPx = with(density) { PlaybackDockHeight.toPx() }
    val chatWidthPx =
        if (landscape && watchState.landscapeChat) {
            with(density) { LandscapeChatWidth.toPx() }
        } else {
            0f
        }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(if (mini) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFromDock by remember { mutableStateOf(false) }
    // Local so a release can choose the resting place before navigation reports the new chrome.
    var committedMini by remember { mutableStateOf(mini) }

    // The player eases in instead of snapping on as a black rectangle over the list.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val appear by animateFloatAsState(if (entered) 1f else 0f, tween(APPEAR_MS), label = "playerAppear")

    // The stream's preview image covers the video until the first frame, then fades out.
    var firstFrame by remember(watchState.channelLogin) { mutableStateOf(false) }
    LaunchedEffect(playback.isPlaying) { if (playback.isPlaying) firstFrame = true }
    val previewAlpha by animateFloatAsState(
        targetValue = if (firstFrame) 0f else 1f,
        animationSpec = tween(PREVIEW_FADE_MS),
        label = "previewAlpha",
    )

    LaunchedEffect(mini) {
        if (!dragging) committedMini = mini
    }
    LaunchedEffect(committedMini, dragging) {
        if (dragging) return@LaunchedEffect
        val target = if (committedMini) 1f else 0f
        if (progress == target) return@LaunchedEffect
        animate(progress, target, animationSpec = DockSpring) { value, _ -> progress = value }
    }

    fun expanded(): PlayerFrame =
        expandedPlayerFrame(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            statusBar = statusBar,
            landscape = landscape,
            chatWidth = chatWidthPx,
        )

    fun docked(): PlayerFrame =
        dockedVideoFrame(
            containerHeight = containerHeight,
            navigationBar = navigationBar,
            dockHeight = dockHeightPx,
        )

    val onDrag: (Float) -> Unit = { delta ->
        dragging = true
        val travel = (docked().top - expanded().top).coerceAtLeast(1f)
        progress = applyPlayerDrag(progress, delta, travel)
    }
    val onDragEnd: (Float) -> Unit = { velocity ->
        when (settlePlayerDrag(progress, velocity)) {
            PlayerDragSettle.Dismiss -> {
                viewModel.close()
            }

            PlayerDragSettle.Mini -> {
                committedMini = true
                if (!mini) onMinimize()
            }

            PlayerDragSettle.Expanded -> {
                committedMini = false
                if (mini) onExpand()
            }
        }
        dragging = false
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged {
                    containerWidth = it.width.toFloat()
                    containerHeight = it.height.toFloat()
                },
    ) {
        if (containerWidth <= 0f) return@Box

        // Position and fade read progress in layout and draw, so a drag does not recompose the
        // tree under the finger. The dock is composed for the whole drag, then faded in.
        if (mini || dragging) {
            DockBar(
                shift = {
                    val travel = (docked().top - expanded().top).coerceAtLeast(1f)
                    if (dragFromDock) (progress - 1f) * travel else ((progress - 1f) * travel).coerceAtLeast(0f)
                },
                alpha = { progress.coerceIn(0f, 1f) },
                displayName = watchState.displayName.ifBlank { watchState.channelLogin },
                title = watchState.title,
                playing = playback.isPlaying,
                onPlayPause = { viewModel.playerHolder.togglePlayPause() },
                onClose = viewModel::close,
                onExpand = onExpand,
                onDragStart = { dragFromDock = true },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                gesturesEnabled = mini,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        Box(
            modifier =
                Modifier
                    .placeFrame {
                        val shown = progress.coerceIn(0f, 1f)
                        val travel = (docked().top - expanded().top).coerceAtLeast(1f)
                        val overscroll = ((progress - 1f) * travel).coerceAtLeast(0f)
                        lerpFrame(expanded(), docked(), shown).let { frame ->
                            frame.copy(top = frame.top + overscroll)
                        }
                    }.graphicsLayer {
                        val travel = (docked().top - expanded().top).coerceAtLeast(1f)
                        val overscroll = ((progress - 1f) * travel).coerceAtLeast(0f)
                        alpha = (1f - overscroll / (dockHeightPx * 1.4f)).coerceIn(0.25f, 1f) * appear
                    }.background(Color.Black),
        ) {
            VideoPlayer(player = viewModel.playerHolder, modifier = Modifier.fillMaxSize())
            val preview = watchState.previewUrl
            if (previewAlpha > 0f && !preview.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previewAlpha }) {
                    AsyncImage(
                        model = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                }
            }
            if (mini) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .playerDrag(
                                onDragStart = { dragFromDock = true },
                                onDrag = onDrag,
                                onDragEnd = onDragEnd,
                            ).dockTap(onExpand),
                )
            }
        }

        if (!mini) {
            val toggleChat =
                if (landscape) {
                    { viewModel.setLandscapeChat(!watchState.landscapeChat) }
                } else {
                    null
                }
            PlayerPane(
                channel = watchState.displayName.ifBlank { watchState.channelLogin },
                state = watchState,
                holder = viewModel.playerHolder,
                onBack = onMinimize,
                onTryAnotherSource = viewModel::playAnotherSource,
                onToggleChat = toggleChat,
                onDoubleTap = toggleChat,
                onVerticalDrag = {
                    dragFromDock = false
                    onDrag(it)
                },
                onVerticalDragEnd = onDragEnd,
                sleepEndsAt = sleepEndsAt,
                onSleepTimer = viewModel::setSleepTimer,
                modifier =
                    Modifier
                        .placeFrame { expanded() }
                        .graphicsLayer { alpha = (1f - progress.coerceIn(0f, 1f)) * appear },
            )
        }
    }
}
