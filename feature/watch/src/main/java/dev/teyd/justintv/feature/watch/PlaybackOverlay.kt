package dev.teyd.justintv.feature.watch

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.player.VideoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Height of the docked bar, not including the navigation bar. Lists should pad by this. */
val PlaybackDockHeight = 64.dp

internal val LandscapeChatWidth = 240.dp

/** Holds the spring-back job so a new drag cancels it. Not snapshot state. */
private class JobSlot {
    var job: Job? = null
}

private val DockSpring = spring<Float>(
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
    if (chrome == PlayerChrome.Hidden || watchState.channelLogin.isBlank()) return

    val mini = chrome == PlayerChrome.Mini
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    val navigationBar = WindowInsets.navigationBars.getBottom(density).toFloat()
    val dockHeightPx = with(density) { PlaybackDockHeight.toPx() }
    val chatWidthPx = if (landscape && watchState.landscapeChat) {
        with(density) { LandscapeChatWidth.toPx() }
    } else {
        0f
    }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    val progress = remember { Animatable(if (mini) 1f else 0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val settle = remember { JobSlot() }

    LaunchedEffect(mini) {
        if (!mini) dragY = 0f
        progress.animateTo(if (mini) 1f else 0f, DockSpring)
    }

    fun expanded(): PlayerFrame = expandedPlayerFrame(
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        statusBar = statusBar,
        landscape = landscape,
        chatWidth = chatWidthPx,
    )

    fun docked(): PlayerFrame = dockedVideoFrame(
        containerHeight = containerHeight,
        navigationBar = navigationBar,
        dockHeight = dockHeightPx,
    )

    val onDrag: (Float) -> Unit = { delta ->
        settle.job?.cancel()
        dragY = (dragY + delta).coerceAtLeast(0f)
    }
    val onDragEnd: (Float) -> Unit = { velocity ->
        settle.job?.cancel()
        if (shouldDismissMini(dragY, dockHeightPx, velocity)) {
            viewModel.close()
        } else {
            val from = dragY
            settle.job = scope.launch {
                animate(from, 0f, animationSpec = DockSpring) { value, _ -> dragY = value }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width.toFloat()
                containerHeight = it.height.toFloat()
            },
    ) {
        if (containerWidth <= 0f) return@Box

        if (mini) {
            DockBar(
                dragY = { dragY },
                displayName = watchState.displayName.ifBlank { watchState.channelLogin },
                title = watchState.title,
                playing = playback.isPlaying,
                onPlayPause = { viewModel.playerHolder.togglePlayPause() },
                onClose = viewModel::close,
                onExpand = onExpand,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        Box(
            modifier = Modifier
                .placeFrame {
                    val frame = lerpFrame(expanded(), docked(), progress.value)
                    frame.copy(top = frame.top + dragY)
                }
                .background(Color.Black),
        ) {
            VideoPlayer(player = viewModel.playerHolder, modifier = Modifier.fillMaxSize())
            if (mini) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .dockGestures(onExpand = onExpand, onDrag = onDrag, onDragEnd = onDragEnd),
                )
            }
        }

        if (!mini) {
            val toggleChat = if (landscape) {
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
                modifier = Modifier.placeFrame { expanded() },
            )
        }
    }
}

@Composable
private fun DockBar(
    dragY: () -> Float,
    displayName: String,
    title: String,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoWidth = PlaybackDockHeight * 16f / 9f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .graphicsLayer { translationY = dragY() }
            .dockGestures(onExpand = onExpand, onDrag = onDrag, onDragEnd = onDragEnd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(PlaybackDockHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(videoWidth))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91916)),
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
    }
}

/**
 * Positions a child at [frame] and sizes the hit target to that rectangle, so the rest of
 * the overlay does not steal touches from the screen underneath.
 */
private fun Modifier.placeFrame(frame: () -> PlayerFrame): Modifier = this
    .offset {
        val placed = frame()
        IntOffset(placed.left.roundToInt(), placed.top.roundToInt())
    }
    .layout { measurable, _ ->
        val placed = frame()
        val width = placed.width.roundToInt().coerceAtLeast(1)
        val height = placed.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/**
 * A tap expands. A downward drag follows the finger and either dismisses or springs back.
 * The work runs in the pointer-input coroutine, so a drag does not start a coroutine per pixel.
 */
private fun Modifier.dockGestures(
    onExpand: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
): Modifier = pointerInput(onExpand) {
    val tracker = VelocityTracker()
    awaitEachGesture {
        // Unconsumed only: play and close sit in this bar and must not also expand it.
        val down = awaitFirstDown()
        tracker.resetTracking()
        var dragging = false
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) onDragEnd(tracker.calculateVelocity().y) else onExpand()
                break
            }
            val delta = change.positionChange().y
            totalY += delta
            if (!dragging && abs(totalY) > viewConfiguration.touchSlop) dragging = true
            if (dragging) {
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                onDrag(delta)
            }
        }
    }
}
