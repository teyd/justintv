package dev.teyd.justintv.feature.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.teyd.justintv.core.player.PlayerHolder
import dev.teyd.justintv.core.player.PlayerStats
import dev.teyd.justintv.core.player.VideoPlayer
import dev.teyd.justintv.core.player.VideoQuality
import kotlinx.coroutines.delay

private val Scrim = Color.Black.copy(alpha = 0.35f)
private val PillBackground = Color.Black.copy(alpha = 0.6f)
private const val CONTROLS_HIDE_DELAY_MS = 3_000L
private const val STATS_REFRESH_MS = 500L
private const val PILL_REFRESH_MS = 1_000L

/**
 * The video, with a deliberately small set of controls drawn on top.
 *
 * Always visible: a pill with the live delay and the source (the proxy, or how the stream is
 * being served). Tap the video for pause, quality and the stats panel. There is no seek bar,
 * skip, or speed control, because none of those mean anything on a live stream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPane(
    channel: String,
    state: WatchUiState,
    holder: PlayerHolder,
    onBack: () -> Unit,
    onTryAnotherSource: () -> Unit,
    onToggleChat: (() -> Unit)?,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playback by holder.playback.collectAsStateWithLifecycle()
    val qualities by holder.qualities.collectAsStateWithLifecycle()
    val selected by holder.selectedQuality.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, touches, playback.isPlaying, showQuality) {
        if (controlsVisible && playback.isPlaying && !showQuality) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    var stats by remember { mutableStateOf(PlayerStats()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(holder, showStats) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = holder.readStats()
                delay(if (showStats) STATS_REFRESH_MS else PILL_REFRESH_MS)
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(onDoubleTap) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap?.invoke() },
                    onTap = {
                        controlsVisible = !controlsVisible
                        touches++
                    },
                )
            },
    ) {
        VideoPlayer(player = holder, modifier = Modifier.fillMaxSize())

        SourcePill(
            text = StatsFormat.pill(stats, state.source),
            verified = state.isVerified,
            onClick = { showStats = !showStats },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )

        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 56.dp),
        ) {
            StatsPanel(lines = StatsFormat.lines(stats, state.source))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Scrim)) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = channel,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = { showStats = !showStats }) {
                        Icon(
                            Icons.Filled.Insights,
                            contentDescription = "Stats",
                            tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    TextButton(onClick = { showQuality = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(text = selected?.label ?: "Auto", color = Color.White)
                    }
                    if (onToggleChat != null) {
                        IconButton(onClick = onToggleChat) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Toggle chat", tint = Color.White)
                        }
                    }
                }

                IconButton(
                    onClick = {
                        holder.togglePlayPause()
                        touches++
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp),
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        if (state.error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = state.error, color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = onTryAnotherSource) { Text("Try again") }
            }
        } else if (state.isLoading || playback.isBuffering) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                if (state.isLoading && state.status.isNotEmpty()) {
                    Text(
                        text = state.status,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PillBackground)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    if (showQuality) {
        ModalBottomSheet(
            onDismissRequest = { showQuality = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            QualitySheet(
                qualities = qualities,
                selected = selected,
                source = state.source,
                onSelect = {
                    holder.selectQuality(it)
                    showQuality = false
                },
                onTryAnotherSource = {
                    showQuality = false
                    onTryAnotherSource()
                },
            )
        }
    }
}

@Composable
private fun SourcePill(
    text: String,
    verified: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(PillBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (verified) Color(0xFF4CAF50) else Color(0xFFFFB300)),
        )
        Text(
            text = text,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatsPanel(lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PillBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        lines.forEach { (label, value) ->
            Row {
                Text(
                    text = label.uppercase().padEnd(8),
                    color = Color(0xFF9E9E9E),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun QualitySheet(
    qualities: List<VideoQuality>,
    selected: VideoQuality?,
    source: String,
    onSelect: (VideoQuality?) -> Unit,
    onTryAnotherSource: () -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Text(
            text = "Quality",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text("Auto") },
            trailingContent = { if (selected == null) Text("✓") },
            modifier = Modifier.clickable { onSelect(null) },
        )
        qualities.forEach { quality ->
            ListItem(
                headlineContent = { Text(quality.label) },
                supportingContent = {
                    if (quality.bitrate > 0) Text(StatsFormat.megabits(quality.bitrate.toLong()))
                },
                trailingContent = { if (quality == selected) Text("✓") },
                modifier = Modifier.clickable { onSelect(quality) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Source") },
            supportingContent = { Text(source.ifBlank { "–" }) },
            trailingContent = { TextButton(onClick = onTryAnotherSource) { Text("Try another") } },
        )
    }
}
