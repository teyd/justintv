package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatMessage
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

/**
 * One channel's chat: connects, reconnects with backoff, loads emotes once the room is known,
 * and emits parsed messages.
 */
class ChatSession(
    private val irc: TwitchIrcClient,
    private val emotes: EmoteRepository,
) {
    val connection = MutableStateFlow(ChatConnection())

    fun messages(login: String): Flow<ChatMessage> = channelFlow {
        val index = AtomicReference(EmoteIndex.EMPTY)
        var emoteJob: Job? = null
        var attempt = 0

        // Loads emotes in the background so the first messages are not held up. Messages that
        // arrive before they are ready show emote names as text, which is acceptable for a
        // second or two.
        fun loadEmotes(roomId: String?, scope: CoroutineScope) {
            emoteJob?.cancel()
            emoteJob = scope.launch(Dispatchers.Default) { index.set(emotes.indexFor(roomId)) }
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
                                ChatMessageParser.parse(message, index.get())?.let { send(it) }
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

    internal companion object {
        /** 1s, 2s, 4s, 8s, then 15s for as long as it keeps failing. */
        fun backoffMs(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(15_000L)
    }
}
