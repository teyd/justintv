package dev.teyd.justintv.feature.watch

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.teyd.justintv.core.chat.ChatStatus
import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.model.ChatSegment
import kotlinx.coroutines.launch

private const val TEXT_SIZE_SP = 14
private const val EMOTE_HEIGHT_SP = 24

/** Chat for the current channel: emotes inline, plain names, newest at the bottom. */
@Composable
fun ChatPane(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = activityChat(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only worth it when the picker can be opened. Keyed on the emote list, which changes when
    // the channel's emotes arrive or a provider is switched off, not on every chat message.
    val context = LocalContext.current
    val canPick = state.composer.visible
    LaunchedEffect(canPick, state.emotes) {
        if (!canPick || state.emotes.isEmpty()) return@LaunchedEffect
        delay(PREFETCH_DELAY_MS)
        prefetchEmoteThumbnails(context, state.emotes)
    }

    Column(modifier = modifier.imePadding()) {
        ChatList(
            messages = state.messages,
            status = state.status,
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
        if (atBottom) following = true else if (listState.isScrollInProgress) following = false
    }
    // Keyed on the newest message, not the list size: the view model caps the list, so the
    // size stops changing while messages keep arriving.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (following && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty() && status != ChatStatus.Connected) {
            Text(
                text = when (status) {
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
                    ChatLine(message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
        if (status == ChatStatus.Reconnecting && messages.isNotEmpty()) {
            Text(
                text = "Reconnecting…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
        }
    }
}

/** `name: message`, with every emote drawn inline at text height. */
@Composable
fun ChatLine(message: ChatMessage, modifier: Modifier = Modifier) {
    val built = remember(message) { buildChatText(message) }

    androidx.compose.material3.Text(
        text = built.text,
        inlineContent = built.inline,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = TEXT_SIZE_SP.sp, lineHeight = (EMOTE_HEIGHT_SP + 2).sp),
        modifier = modifier.fillMaxWidth(),
    )
}

private class BuiltChat(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

private fun buildChatText(message: ChatMessage): BuiltChat {
    val inline = HashMap<String, InlineTextContent>()
    val nameColor = parseChatColor(message.color)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = nameColor)) { append(message.user) }
        append(if (message.isAction) " " else ": ")
        message.segments.forEachIndexed { index, segment ->
            when (segment) {
                is ChatSegment.Text -> append(segment.text)
                is ChatSegment.Emote -> {
                    val id = "e$index"
                    appendInlineContent(id, segment.name)
                    inline[id] = InlineTextContent(
                        Placeholder(
                            width = (EMOTE_HEIGHT_SP * segment.aspectRatio).sp,
                            height = EMOTE_HEIGHT_SP.sp,
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
