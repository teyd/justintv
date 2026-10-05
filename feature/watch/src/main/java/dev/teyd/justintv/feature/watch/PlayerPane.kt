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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.teyd.justintv.core.model.formatUptime
import dev.teyd.justintv.core.model.formatViewers
import dev.teyd.justintv.core.player.PlayerHolder
import dev.teyd.justintv.core.player.PlayerStats
import dev.teyd.justintv.core.player.VideoQuality
import kotlinx.coroutines.delay
import java.time.Instant

private val Scrim = Color.Black.copy(alpha = 0.35f)
private val PillBackground = Color.Black.copy(alpha = 0.6f)
private const val CONTROLS_HIDE_DELAY_MS = 3_000L
private const val STATS_REFRESH_MS = 500L
private const val UPTIME_REFRESH_MS = 30_000L
private const val SLOW_START_MS = 1_500L

private fun sleepOptionLabel(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60} hour" + if (minutes == 60) "" else "s" else "$minutes minutes"

/**
 * Controls drawn on top of the video. The surface itself lives in the playback overlay, so this
 * pane is transparent and never owns a second PlayerView.
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
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: (Float) -> Unit = {},
    sleepEndsAt: Long? = null,
    onSleepTimer: (Int?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playback by holder.playback.collectAsStateWithLifecycle()
    val qualities by holder.qualities.collectAsStateWithLifecycle()
    val selected by holder.selectedQuality.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    val drag = rememberUpdatedState(onVerticalDrag)
    val dragEnd = rememberUpdatedState(onVerticalDragEnd)
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        if (sleepEndsAt == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sleepLabel = sleepEndsAt?.let { formatSleepRemaining(it - now) }

    // Uptime is derived from the start time, so it only needs a new "now" now and then.
    var clock by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.startedAt) {
        if (state.startedAt == null) return@LaunchedEffect
        while (true) {
            clock = Instant.now()
            delay(UPTIME_REFRESH_MS)
        }
    }
    val uptime = remember(state.startedAt, clock) { formatUptime(state.startedAt, clock) }

    // Resolving a source is usually quick. Technical status text only helps when it is not, so
    // it waits a moment instead of flashing past.
    val resolving = state.isLoading && state.error == null
    val rebuffering = playback.isBuffering && !state.isLoading && state.error == null
    var slowToStart by remember { mutableStateOf(false) }
    LaunchedEffect(resolving) {
        slowToStart = false
        if (resolving) {
            delay(SLOW_START_MS)
            slowToStart = true
        }
    }

    LaunchedEffect(controlsVisible, touches, playback.isPlaying, showQuality) {
        if (controlsVisible && playback.isPlaying && !showQuality) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    var stats by remember { mutableStateOf(PlayerStats()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(holder, showStats) {
        // The pill and the panel only exist while the viewer asked for stats, so the reads only
        // run then; pressing the geek glyph shows a snapshot immediately.
        if (!showStats) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = holder.readStats()
                delay(STATS_REFRESH_MS)
            }
        }
    }

    Box(
        modifier =
            modifier
                .playerDrag(onDrag = { drag.value(it) }, onDragEnd = { dragEnd.value(it) })
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
        // Hidden until the viewer presses the stats glyph: the source and live delay are
        // geek details, not part of the default viewing surface.
        if (showStats) {
            SourcePill(
                text = StatsFormat.pill(stats, state.proxy),
                verified = state.isVerified,
                onClick = { showStats = false },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 40.dp),
            )
        }

        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 56.dp),
        ) {
            StatsPanel(lines = StatsFormat.lines(stats, state.proxy))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Scrim)) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimise", tint = Color.White)
                    }
                    Text(
                        text = channel,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = { showSleep = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (sleepLabel != null) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (sleepLabel != null) {
                        Text(text = sleepLabel, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
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

                // Shown with the controls only: how many are watching, and how long it has been up.
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.viewers?.let { count ->
                        InfoPill(icon = Icons.Filled.Person, text = formatViewers(count), description = "Viewers")
                    } ?: Spacer(Modifier.size(1.dp))
                    uptime?.let { text ->
                        InfoPill(icon = Icons.Filled.Schedule, text = text, description = "Uptime")
                    }
                }

                // No play button while the source is still being found: there is nothing to play.
                if (!state.isLoading) {
                    IconButton(
                        onClick = {
                            holder.togglePlayPause()
                            touches++
                        },
                        modifier =
                            Modifier
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
        }

        if (state.error != null) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = state.error, color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = onTryAnotherSource) { Text("Try again") }
            }
        }

        // Mid-stream stalls get a small spinner. Starting up gets a thin bar along the bottom
        // edge over the preview, and the status line only if starting is taking a while.
        AnimatedVisibility(
            visible = rebuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = Color.White, strokeWidth = 3.dp)
        }
        AnimatedVisibility(
            visible = resolving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = slowToStart && state.status.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = state.status,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }
    }

    if (showSleep) {
        ModalBottomSheet(
            onDismissRequest = { showSleep = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Text(
                    text = "Sleep timer",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = "Stops the stream so the screen can sleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ListItem(
                    headlineContent = { Text("Off") },
                    trailingContent = { if (sleepEndsAt == null) Text("✓") },
                    modifier =
                        Modifier.clickable {
                            onSleepTimer(null)
                            showSleep = false
                        },
                )
                SLEEP_TIMER_MINUTES.forEach { minutes ->
                    ListItem(
                        headlineContent = { Text(sleepOptionLabel(minutes)) },
                        modifier =
                            Modifier.clickable {
                                onSleepTimer(minutes)
                                showSleep = false
                            },
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
private fun InfoPill(
    icon: ImageVector,
    text: String,
    description: String,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(PillBackground)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelMedium)
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
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(PillBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
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
private fun StatsPanel(
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
