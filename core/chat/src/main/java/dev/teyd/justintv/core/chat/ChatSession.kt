package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatMessage
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatStatus { Connecting, Connected, Reconnecting }

/** What the chat screen needs to know about the connection. */
data class ChatConnection(val status: ChatStatus = ChatStatus.Connecting)

/** What to load from the recent-messages history service when chat opens. */
data class ChatHistorySettings(
    val enabled: Boolean = true,
    val limit: Int = RecentMessages.DEFAULT_LIMIT,
)

/**
 * One channel's chat: connects, reconnects with backoff, loads emotes once the room is known,
 * and emits parsed messages.
 */
class ChatSession(
    private val irc: TwitchIrcClient,
    private val emoteRepository: EmoteRepository,
    private val recent: RecentMessages,
    private val historySettings: suspend () -> ChatHistorySettings = { ChatHistorySettings() },
) {
    val connection = MutableStateFlow(ChatConnection())

    /** Emotes for the open channel, for the picker. Empty until the room is known. */
    val emotes = MutableStateFlow<List<Emote>>(emptyList())

    fun messages(login: String): Flow<ChatMessage> = channelFlow {
        val index = AtomicReference(EmoteIndex.EMPTY)
        val seenIds = Collections.synchronizedSet(HashSet<String>())
        var emoteJob: Job? = null
        var attempt = 0

        launch {
            val settings = historySettings()
            if (!settings.enabled) return@launch
            val history = recent.fetch(login, limit = settings.limit)
            val roomId = history.firstNotNullOfOrNull { it.tags["room-id"] }
            index.set(loadEmoteIndex(roomId))
            history.forEach { line ->
                val message = ChatMessageParser.parse(line, index.get()) ?: return@forEach
                if (seenIds.add(message.id)) send(message)
            }
        }

        fun loadEmotes(roomId: String?, scope: CoroutineScope) {
            emoteJob?.cancel()
            emoteJob = scope.launch(Dispatchers.Default) { index.set(loadEmoteIndex(roomId)) }
        }

        while (true) {
            connection.update { it.copy(status = if (attempt == 0) ChatStatus.Connecting else ChatStatus.Reconnecting) }
            try {
                irc.events(login).collect { event ->
                    when (event) {
                        IrcEvent.Connected -> {
                            attempt = 0
                            connection.update { it.copy(status = ChatStatus.Connected) }
                        }

                        is IrcEvent.Line -> {
                            val message = event.message
                            if (message.command == "ROOMSTATE") {
                                message.tags["room-id"]?.let { loadEmotes(it, this@channelFlow) }
                            } else {
                                val parsed = ChatMessageParser.parse(message, index.get()) ?: return@collect
                                if (seenIds.add(parsed.id)) send(parsed)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to the backoff below.
            }
            connection.update { it.copy(status = ChatStatus.Reconnecting) }
            attempt++
            delay(backoffMs(attempt))
        }
    }

    private suspend fun loadEmoteIndex(roomId: String?): EmoteIndex {
        val index = emoteRepository.indexFor(roomId)
        emotes.value = index.emotes()
        return index
    }

    suspend fun send(channelLogin: String, nick: String, accessToken: String, text: String) {
        irc.sendMessage(channelLogin, nick, accessToken, text)
    }

    internal companion object {
        /** 1s, 2s, 4s, 8s, then 15s for as long as it keeps failing. */
        fun backoffMs(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(15_000L)
    }
}
