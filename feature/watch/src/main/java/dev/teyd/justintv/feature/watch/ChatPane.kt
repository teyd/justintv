package dev.teyd.justintv.feature.watch

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.teyd.justintv.core.chat.ChatStatus
import dev.teyd.justintv.core.data.ChatTextSize
import dev.teyd.justintv.core.data.ChatTimeFormat
import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.model.ChatSegment
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chat for the current channel: emotes inline, names and text sized by the viewer's settings. */
@Composable
fun ChatPane(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = activityChat(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.imePadding()) {
        ChatList(
            messages = state.messages,
            status = state.status,
            coloredUsernames = state.coloredUsernames,
            textSize = state.chatTextSize,
            showTimestamps = state.showTimestamps,
            timeFormat = state.timeFormat,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        ChatComposer(
            state = state.composer,
            emotes = state.emotes,
            onSend = viewModel::send,
            onAllowChat = viewModel::allowChat,
        )
    }
}

@Composable
fun ChatList(
    messages: List<ChatMessage>,
    status: ChatStatus,
    coloredUsernames: Boolean = true,
    textSize: ChatTextSize = ChatTextSize.Default,
    showTimestamps: Boolean = false,
    timeFormat: ChatTimeFormat = ChatTimeFormat.System,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Follow the newest message until the viewer scrolls up to read, then stop yanking them down.
    var following by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }

    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (atBottom) {
            following = true
        } else if (listState.isScrollInProgress) {
            following = false
        }
    }
    // Keyed on the newest message, not the list size: the view model caps the list, so the
    // size stops changing while messages keep arriving.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (following && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty() && status != ChatStatus.Connected) {
            Text(
                text =
                    when (status) {
                        ChatStatus.Connecting -> "Connecting to chat…"
                        ChatStatus.Reconnecting -> "Chat disconnected, reconnecting…"
                        ChatStatus.Connected -> ""
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatLine(
                        message = message,
                        coloredUsernames = coloredUsernames,
                        textSize = textSize,
                        showTimestamp = showTimestamps,
                        timeFormat = timeFormat,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (!following && messages.isNotEmpty()) {
            AssistChip(
                onClick = {
                    following = true
                    scope.launch { listState.scrollToItem(messages.lastIndex) }
                },
                label = { Text("Newer messages") },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
            )
        }
        if (status == ChatStatus.Reconnecting && messages.isNotEmpty()) {
            Text(
                text = "Reconnecting…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
            )
        }
    }
}

/** `name: message`, with inline emotes and accessible, underlined web links. */
@Composable
fun ChatLine(
    message: ChatMessage,
    coloredUsernames: Boolean = true,
    textSize: ChatTextSize = ChatTextSize.Default,
    showTimestamp: Boolean = false,
    timeFormat: ChatTimeFormat = ChatTimeFormat.System,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    // Unspecified leaves the name on the text's own colour, which is what "no colours" means.
    val nameColor = if (coloredUsernames) parseChatColor(message.color) else Color.Unspecified
    val timestampColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    val is24Hour =
        when (timeFormat) {
            ChatTimeFormat.System -> DateFormat.is24HourFormat(context)
            ChatTimeFormat.Hour12 -> false
            ChatTimeFormat.Hour24 -> true
        }
    val timestamp = if (showTimestamp) message.timestampMs?.let { formatChatTime(it, is24Hour = is24Hour) } else null
    val built =
        remember(message, linkColor, nameColor, textSize, timestamp, timestampColor) {
            buildChatText(
                message = message,
                linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                nameColor = nameColor,
                textSize = textSize,
                timestamp = timestamp,
                timestampColor = timestampColor,
            )
        }

    androidx.compose.material3.Text(
        text = built.text,
        inlineContent = built.inline,
        style =
            MaterialTheme.typography.bodyMedium.copy(
                fontSize = textSize.textSp.sp,
                lineHeight = (textSize.emoteSp + 2).sp,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Local wall-clock time for a chat line: `14:05` in 24-hour, `2:05 PM` in 12-hour. */
internal fun formatChatTime(
    timestampMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    is24Hour: Boolean = true,
): String =
    Instant
        .ofEpochMilli(timestampMs)
        .atZone(zone)
        .format(if (is24Hour) hour24Formatter else hour12Formatter)

private val hour24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val hour12Formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal class BuiltChat(
    val text: AnnotatedString,
    val inline: Map<String, InlineTextContent>,
)

internal fun buildChatText(
    message: ChatMessage,
    linkStyles: TextLinkStyles,
    nameColor: Color = parseChatColor(message.color),
    textSize: ChatTextSize = ChatTextSize.Default,
    timestamp: String? = null,
    timestampColor: Color = Color.Unspecified,
): BuiltChat {
    val inline = HashMap<String, InlineTextContent>()
    val emoteHeight = textSize.emoteSp
    // A touch smaller than emotes, and it follows the text-size setting like they do.
    val badgeSize = textSize.emoteSp - 6
    val text =
        buildAnnotatedString {
            if (timestamp != null) {
                withStyle(SpanStyle(color = timestampColor)) { append(timestamp) }
                append(" ")
            }
            message.badges.forEachIndexed { index, badge ->
                val id = "b$index"
                appendInlineContent(id, badge.title)
                inline[id] =
                    InlineTextContent(
                        Placeholder(
                            width = badgeSize.sp,
                            height = badgeSize.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                    ) {
                        AsyncImage(
                            model = badge.url,
                            contentDescription = badge.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(end = 2.dp),
                        )
                    }
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = nameColor)) { append(message.user) }
            append(if (message.isAction) " " else ": ")
            message.segments.forEachIndexed { index, segment ->
                when (segment) {
                    is ChatSegment.Text -> {
                        appendChatLinks(segment.text, linkStyles)
                    }

                    is ChatSegment.Emote -> {
                        val id = "e$index"
                        appendInlineContent(id, segment.name)
                        inline[id] =
                            InlineTextContent(
                                Placeholder(
                                    width = (emoteHeight * segment.aspectRatio).sp,
                                    height = emoteHeight.sp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                                ),
                            ) {
                                AsyncImage(
                                    model = segment.url,
                                    contentDescription = segment.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                    }
                }
            }
        }
    return BuiltChat(text, inline)
}

/** `#RRGGBB` from the chat model becomes a Compose colour. Falls back to white on garbage. */
internal fun parseChatColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return Color.White
    val value = cleaned.toLongOrNull(16) ?: return Color.White
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f,
    )
}
