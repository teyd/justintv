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

@Composable
internal fun DockBar(
    shift: () -> Float,
    alpha: () -> Float,
    displayName: String,
    title: String,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val videoWidth = PlaybackDockHeight * 16f / 9f
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .graphicsLayer {
                    translationY = shift()
                    this.alpha = alpha()
                }.then(
                    if (gesturesEnabled) {
                        Modifier.playerDrag(onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd)
                    } else {
                        Modifier
                    },
                ).then(if (gesturesEnabled) Modifier.dockTap(onExpand) else Modifier),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(PlaybackDockHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(videoWidth))
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
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
internal fun Modifier.placeFrame(frame: () -> PlayerFrame): Modifier =
    this
        .offset {
            val placed = frame()
            IntOffset(placed.left.roundToInt(), placed.top.roundToInt())
        }.layout { measurable, _ ->
            val placed = frame()
            val width = placed.width.roundToInt().coerceAtLeast(1)
            val height = placed.height.roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) { placeable.place(0, 0) }
        }

/**
 * Moves the player with the finger. One pixel of drag is reported as one pixel; the overlay
 * turns that into a collapse fraction. Runs in the pointer-input coroutine.
 */
internal fun Modifier.playerDrag(
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
): Modifier =
    pointerInput(Unit) {
        val tracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            tracker.resetTracking()
            var moved = false
            var totalY = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (moved) onDragEnd(tracker.calculateVelocity().y)
                    break
                }
                val delta = change.positionChange().y
                totalY += delta
                if (!moved && abs(totalY) > viewConfiguration.touchSlop) {
                    moved = true
                    onDragStart()
                }
                if (moved) {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    onDrag(delta)
                }
            }
        }
    }

/** A tap on the dock, ignored once the finger has started a drag. */
internal fun Modifier.dockTap(onExpand: () -> Unit): Modifier =
    pointerInput(onExpand) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var total = 0f
            var dragged = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!dragged) onExpand()
                    break
                }
                total += abs(change.positionChange().y)
                if (total > viewConfiguration.touchSlop) dragged = true
            }
        }
    }
