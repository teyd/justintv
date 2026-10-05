package dev.teyd.justintv.feature.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.player.VideoPlayer
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val MiniWidth = 168.dp
private val MiniHeight = MiniWidth * 9f / 16f
private val MiniMargin = 12.dp

/**
 * A small live window that sits over the rest of the app.
 *
 * Drag it sideways and let go: the card flings to the nearer corner, or to the corner it was
 * thrown toward. Drag it down to dismiss it. Tap to open the full player; the buttons pause
 * playback and close the card.
 *
 * [visible] drives a Material motion enter and exit; the card stays composed until the exit
 * animation has finished, so the player only leaves the tree once it has animated out.
 */
@Composable
fun MiniPlayer(
    viewModel: WatchViewModel,
    visible: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val side by viewModel.miniSide.collectAsStateWithLifecycle()
    val playback by viewModel.playerHolder.playback.collectAsStateWithLifecycle()
    val watchState by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val widthPx = with(density) { MiniWidth.toPx() }
    val heightPx = with(density) { MiniHeight.toPx() }
    val marginPx = with(density) { MiniMargin.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }

    val rightX = (containerWidth - widthPx - marginPx).coerceAtLeast(marginPx)
    val restingX = if (side == MiniSide.Left) marginPx else rightX
    val snapSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // Follow the container size and the chosen side. The first placement snaps so the card
    // does not slide in from the left on the first frame it is measured.
    LaunchedEffect(containerWidth, side) {
        if (containerWidth <= 0f) return@LaunchedEffect
        if (placed) {
            offsetX.animateTo(restingX, snapSpec)
        } else {
            offsetX.snapTo(restingX)
            placed = true
        }
    }

    // A dismissed card keeps its dragged-down offset through the exit animation; reset it so
    // the next appearance starts at the resting position.
    LaunchedEffect(visible) {
        if (visible) offsetY.snapTo(0f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .navigationBarsPadding()
            .padding(bottom = MiniMargin),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .graphicsLayer {
                        alpha = (1f - offsetY.value / (heightPx * 1.2f)).coerceIn(0.2f, 1f)
                    }
                    .size(width = MiniWidth, height = MiniHeight)
                    .draggable(
                        state = rememberDraggableState { delta ->
                            val maxX = (containerWidth - widthPx).coerceAtLeast(0f)
                            // snapTo is suspend, and issuing it also cancels any settle animation
                            // still in flight, so a new drag takes over the card immediately.
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(0f, maxX))
                            }
                        },
                        orientation = Orientation.Horizontal,
                        enabled = visible,
                        onDragStopped = { velocity ->
                            val target = snapMiniSide(offsetX.value + widthPx / 2f, containerWidth, velocity)
                            if (target == side) {
                                // The side did not change, so the resting LaunchedEffect will not run.
                                scope.launch {
                                    offsetX.animateTo(if (target == MiniSide.Left) marginPx else rightX, snapSpec)
                                }
                            } else {
                                viewModel.setMiniSide(target)
                            }
                        },
                    )
                    .draggable(
                        state = rememberDraggableState { delta ->
                            // Downward only: the card follows the finger toward the dismissal
                            // threshold, but an upward drag springs back instead of lifting away.
                            scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
                        },
                        orientation = Orientation.Vertical,
                        enabled = visible,
                        onDragStopped = { velocity ->
                            if (shouldDismissMini(offsetY.value, heightPx, velocity)) {
                                viewModel.close()
                            } else {
                                scope.launch { offsetY.animateTo(0f, snapSpec) }
                            }
                        },
                    )
                    .pointerInput(visible) {
                        if (!visible) return@pointerInput
                        detectTapGestures(onTap = { onExpand() })
                    },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.scrim,
                contentColor = Color.White,
                shadowElevation = 6.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoPlayer(player = viewModel.playerHolder, modifier = Modifier.fillMaxSize())
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledIconButton(
                            onClick = { viewModel.playerHolder.togglePlayPause() },
                            colors = miniButtonColors(),
                        ) {
                            Icon(
                                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                            )
                        }
                        FilledIconButton(
                            onClick = viewModel::close,
                            colors = miniButtonColors(),
                        ) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                    val channel = watchState.channelLogin
                    if (channel.isNotBlank()) {
                        Text(
                            text = channel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun miniButtonColors() = IconButtonDefaults.filledIconButtonColors(
    containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
    contentColor = LocalContentColor.current,
)
