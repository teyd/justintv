package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

enum class ChatStatus { Connecting, Connected, Reconnecting }

/** What the chat screen needs to know about the connection. */
data class ChatConnection(
    val status: ChatStatus = ChatStatus.Connecting,
)

/** What to load from the recent-messages history service when chat opens. */
data class ChatHistorySettings(
    val enabled: Boolean = true,
    val limit: Int = RecentMessages.DEFAULT_LIMIT,
)

/**
 * A chat line, or the same line again once a badge resolved after it was sent.
 *
 * Only 7TV badges arrive late; the other sources are part of the badge index. The screen
 * replaces the line it already has instead of showing it twice.
 */
sealed interface ChatEvent {
    data class New(
        val message: ChatMessage,
    ) : ChatEvent

    data class Updated(
        val message: ChatMessage,
    ) : ChatEvent
}

/**
 * One channel's chat: connects, reconnects with backoff, loads emotes and badges once the room
 * is known, and emits parsed messages.
 */
class ChatSession(
    private val irc: TwitchIrcClient,
    private val emoteRepository: EmoteRepository,
    private val badgeRepository: BadgeRepository,
    private val sevenTvBadges: SevenTvBadges,
    private val recent: RecentMessages,
    private val historySettings: suspend () -> ChatHistorySettings = { ChatHistorySettings() },
) {
    val connection = MutableStateFlow(ChatConnection())

    /** Emotes for the open channel, for the picker. Empty until the room is known. */
    val emotes = MutableStateFlow<List<Emote>>(emptyList())

    private val reloadEmotes = AtomicReference<(() -> Unit)?>(null)
    private val reloadBadges = AtomicReference<(() -> Unit)?>(null)

    /** Loads again, for example once a token exists so Twitch emotes and badges can be fetched. */
    fun refreshEmotes() {
        reloadEmotes.get()?.invoke()
    }

    /** Loads again after a badge setting changed, so a source starts or stops applying. */
    fun refreshBadges() {
        reloadBadges.get()?.invoke()
    }

    fun messages(login: String): Flow<ChatEvent> =
        channelFlow {
            val emoteIndex = AtomicReference(EmoteIndex.EMPTY)
            val badgeIndex = AtomicReference(BadgeIndex.EMPTY)
            val seenIds = Collections.synchronizedSet(HashSet<String>())
            val roomId = AtomicReference<String?>(null)
            var emoteJob: Job? = null
            var badgeJob: Job? = null
            var attempt = 0

            fun loadEmotes(id: String?) {
                if (id != null) roomId.set(id)
                emoteJob?.cancel()
                emoteJob = launch(Dispatchers.Default) { emoteIndex.set(loadEmoteIndex(roomId.get())) }
            }

            fun loadBadges(id: String?) {
                if (id != null) roomId.set(id)
                badgeJob?.cancel()
                badgeJob = launch(Dispatchers.Default) { badgeIndex.set(loadBadgeIndex(roomId.get())) }
            }

            val reloadEmoteIndex = { loadEmotes(roomId.get()) }
            val reloadBadgeIndex = { loadBadges(roomId.get()) }
            reloadEmotes.set(reloadEmoteIndex)
            reloadBadges.set(reloadBadgeIndex)

            // 7TV badges are resolved per user, so a line is emitted first without them and
            // emitted again once the lookup answers. The queue coalesces a message burst into
            // one batched query instead of one request per chatter.
            val sevenTvQueue = Channel<Pair<String, ChatMessage>>(Channel.UNLIMITED)
            val backlog = HashMap<String, MutableList<ChatMessage>>()

            suspend fun emit(
                message: ChatMessage,
                userId: String?,
            ) {
                if (!badgeIndex.get().sevenTv || userId == null) {
                    send(ChatEvent.New(message))
                    return
                }
                val badge = sevenTvBadges.cached(userId)
                if (badge != null) {
                    send(ChatEvent.New(message.copy(badges = message.badges + badge)))
                    return
                }
                send(ChatEvent.New(message))
                if (!sevenTvBadges.isKnown(userId)) sevenTvQueue.trySend(userId to message)
            }

            launch {
                while (true) {
                    val first = sevenTvQueue.receive()
                    backlog.getOrPut(first.first) { mutableListOf() } += first.second
                    delay(SEVENTV_COALESCE_MS)
                    while (true) {
                        val next = sevenTvQueue.tryReceive().getOrNull() ?: break
                        backlog.getOrPut(next.first) { mutableListOf() } += next.second
                    }
                    val batch = HashMap(backlog)
                    backlog.clear()
                    val resolved =
                        try {
                            sevenTvBadges.resolve(batch.keys)
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Nothing cached: the next message from these chatters retries.
                            false
                        }
                    if (!resolved) continue
                    for ((userId, messages) in batch) {
                        val badge = sevenTvBadges.cached(userId) ?: continue
                        messages.forEach { send(ChatEvent.Updated(it.copy(badges = it.badges + badge))) }
                    }
                }
            }

            launch {
                val settings = historySettings()
                if (!settings.enabled) return@launch
                val history = recent.fetch(login, limit = settings.limit)
                val id = history.firstNotNullOfOrNull { it.tags["room-id"] }
                if (id != null) roomId.set(id)
                // Synchronous: these lines are parsed with the indexes, so they have to exist first.
                emoteIndex.set(loadEmoteIndex(roomId.get()))
                badgeIndex.set(loadBadgeIndex(roomId.get()))
                history.forEach { line ->
                    val message = ChatMessageParser.parse(line, emoteIndex.get(), badgeIndex.get()) ?: return@forEach
                    if (seenIds.add(message.id)) emit(message, line.tags["user-id"])
                }
            }

            try {
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
                                        message.tags["room-id"]?.let { roomId ->
                                            loadEmotes(roomId)
                                            loadBadges(roomId)
                                        }
                                    } else {
                                        val parsed =
                                            ChatMessageParser
                                                .parse(message, emoteIndex.get(), badgeIndex.get())
                                                ?: return@collect
                                        if (seenIds.add(parsed.id)) emit(parsed, message.tags["user-id"])
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
            } finally {
                reloadEmotes.compareAndSet(reloadEmoteIndex, null)
                reloadBadges.compareAndSet(reloadBadgeIndex, null)
            }
        }

    private suspend fun loadEmoteIndex(roomId: String?): EmoteIndex {
        val index = emoteRepository.indexFor(roomId)
        emotes.value = index.picker
        return index
    }

    private suspend fun loadBadgeIndex(roomId: String?): BadgeIndex = badgeRepository.indexFor(roomId)

    suspend fun send(
        channelLogin: String,
        nick: String,
        accessToken: String,
        text: String,
    ) {
        irc.sendMessage(channelLogin, nick, accessToken, text)
    }

    internal companion object {
        /** 1s, 2s, 4s, 8s, then 15s for as long as it keeps failing. */
        fun backoffMs(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(15_000L)

        /** How long a 7TV lookup waits for more chatters before sending one batched query. */
        const val SEVENTV_COALESCE_MS = 120L
    }
}
