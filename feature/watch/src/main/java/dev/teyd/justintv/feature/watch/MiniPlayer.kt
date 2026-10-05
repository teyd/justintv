package dev.teyd.justintv.feature.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.player.VideoPlayer
import kotlin.math.roundToInt

private val MiniWidth = 168.dp
private val MiniMargin = 12.dp

/**
 * A small live window that sits over the rest of the app.
 *
 * Drag it and let go: the left half of the screen snaps to the bottom left, the right half to
 * the bottom right. Tap to open the full player. The close button stops playback.
 */
@Composable
fun MiniPlayer(
    viewModel: WatchViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val side by viewModel.miniSide.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val widthPx = with(density) { MiniWidth.toPx() }
    val marginPx = with(density) { MiniMargin.toPx() }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(Float.NaN) }

    val restingX = if (side == MiniSide.Left) marginPx else (containerWidth - widthPx - marginPx).coerceAtLeast(marginPx)
    val shownX = if (offsetX.isNaN()) restingX else offsetX

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .navigationBarsPadding()
            .padding(bottom = MiniMargin),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(shownX.roundToInt(), 0) }
                .size(width = MiniWidth, height = MiniWidth * 9f / 16f)
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .pointerInput(containerWidth) {
                    detectDragGestures(
                        onDragEnd = {
                            val sideNow = snapMiniSide(shownX + widthPx / 2f, containerWidth)
                            viewModel.setMiniSide(sideNow)
                            offsetX = Float.NaN
                        },
                        onDragCancel = { offsetX = Float.NaN },
                        onDrag = { change, drag ->
                            change.consume()
                            val current = if (offsetX.isNaN()) restingX else offsetX
                            offsetX = (current + drag.x).coerceIn(0f, (containerWidth - widthPx).coerceAtLeast(0f))
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onExpand() })
                },
        ) {
            VideoPlayer(player = viewModel.playerHolder, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = viewModel::close,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
