package dev.teyd.justintv.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.chat.ChatSendException
import dev.teyd.justintv.core.chat.ChatSession
import dev.teyd.justintv.core.chat.ChatStatus
import dev.teyd.justintv.core.chat.Emote
import dev.teyd.justintv.core.chat.EmoteSource
import dev.teyd.justintv.core.data.ChatSettingsStore
import dev.teyd.justintv.core.data.ChatTextSize
import dev.teyd.justintv.core.data.ChatTimeFormat
import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.network.AuthState
import dev.teyd.justintv.core.network.TwitchSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: ChatStatus = ChatStatus.Connecting,
    val emotes: List<Emote> = emptyList(),
    val composer: ComposerState = ComposerState(),
    val coloredUsernames: Boolean = true,
    val chatTextSize: ChatTextSize = ChatTextSize.Default,
    val showTimestamps: Boolean = false,
    val timeFormat: ChatTimeFormat = ChatTimeFormat.System,
)

/**
 * Chat for the channel being watched.
 *
 * Scoped to the activity, like the player. Minimising pops the watch screen, and a
 * destination-scoped chat would drop the connection and the lines already on screen.
 * [close] is what actually stops it, when playback stops.
 *
 * Incoming messages are collected for a short window and published together. A busy chat can
 * deliver dozens of lines a second, and one list update per line would make the screen stutter.
 */
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val session: ChatSession,
        private val twitch: TwitchSession,
        private val chatSettings: ChatSettingsStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChatUiState())
        val state: StateFlow<ChatUiState> = _state.asStateFlow()

        private var login: String = ""
        private var collectJob: Job? = null
        private val pending = ArrayList<ChatMessage>()
        private var flushJob: Job? = null
        private var showInput = true
        private var auth: AuthState = AuthState.LoggedOut
        private var sendError: String? = null

        init {
            viewModelScope.launch {
                chatSettings.showInput.collect {
                    showInput = it
                    publishComposer()
                }
            }
            viewModelScope.launch {
                combine(chatSettings.coloredUsernames, chatSettings.chatTextSize) { colored, size -> colored to size }
                    .collect { (colored, size) ->
                        _state.update { it.copy(coloredUsernames = colored, chatTextSize = size) }
                    }
            }
            viewModelScope.launch {
                chatSettings.showTimestamps.collect { enabled ->
                    _state.update { it.copy(showTimestamps = enabled) }
                }
            }
            viewModelScope.launch {
                chatSettings.timeFormat.collect { format ->
                    _state.update { it.copy(timeFormat = format) }
                }
            }
            viewModelScope.launch {
                twitch.state.collect { next ->
                    val signedIn = next is AuthState.LoggedIn && auth !is AuthState.LoggedIn
                    auth = next
                    publishComposer()
                    // The first index may have run before the token was restored. Twitch emotes
                    // need that token; third-party sets are already cached.
                    if (signedIn) session.refreshEmotes()
                }
            }
        }

        /** Keeps the current room if [channel] is the one already open. */
        fun open(channel: String) {
            if (channel.isBlank() || channel == login) return
            stop()
            login = channel
            resetChannelState()
            publishComposer()
            collectJob =
                viewModelScope.launch {
                    launch {
                        session.connection.collect { connection ->
                            _state.update { it.copy(status = connection.status) }
                        }
                    }
                    launch {
                        // The index was built with whatever was switched on at the time. Filtering here
                        // makes a provider disappear from the picker as soon as it is turned off.
                        combine(
                            session.emotes,
                            chatSettings.sevenTv,
                            chatSettings.bttv,
                            chatSettings.ffz,
                        ) { loaded, sevenTv, bttv, ffz ->
                            loaded.filter { emote ->
                                when (emote.source) {
                                    EmoteSource.Twitch -> true
                                    EmoteSource.SevenTv -> sevenTv
                                    EmoteSource.Bttv -> bttv
                                    EmoteSource.Ffz -> ffz
                                }
                            }
                        }.collect { enabled ->
                            _state.update { it.copy(emotes = enabled) }
                        }
                    }
                    session.messages(channel).collect { message ->
                        pending += message
                        scheduleFlush()
                    }
                }
        }

        fun send(text: String) {
            val signedIn = auth as? AuthState.LoggedIn ?: return
            if (!signedIn.canChat) {
                twitch.grantChat()
                return
            }
            val channel = login
            viewModelScope.launch {
                sendError = null
                publishComposer()
                try {
                    val token = twitch.accessToken() ?: throw ChatSendException("Not signed in")
                    session.send(channel, signedIn.login, token, text)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sendError = e.message ?: "Could not send"
                    publishComposer()
                }
            }
        }

        fun allowChat() = twitch.grantChat()

        /** Drops the room. Called when playback stops, not when the watch screen is minimised. */
        fun close() {
            stop()
            resetChannelState()
        }

        /** Wipes the channel but keeps preferences, which do not depend on the room. */
        private fun resetChannelState() {
            _state.update {
                ChatUiState(
                    coloredUsernames = it.coloredUsernames,
                    chatTextSize = it.chatTextSize,
                    showTimestamps = it.showTimestamps,
                    timeFormat = it.timeFormat,
                )
            }
        }

        private fun stop() {
            login = ""
            collectJob?.cancel()
            collectJob = null
            flushJob?.cancel()
            flushJob = null
            pending.clear()
        }

        private fun publishComposer() {
            val signedIn = auth as? AuthState.LoggedIn
            val pending = auth as? AuthState.Pending
            _state.update {
                it.copy(
                    composer =
                        ComposerState(
                            visible = showInput && (signedIn != null || pending != null),
                            canSend = signedIn?.canChat == true,
                            needsChatPermission = signedIn != null && !signedIn.canChat,
                            approvalCode = pending?.userCode,
                            error = sendError,
                        ),
                )
            }
        }

        private fun scheduleFlush() {
            if (flushJob?.isActive == true) return
            flushJob =
                viewModelScope.launch {
                    delay(FLUSH_WINDOW_MS)
                    val batch = ArrayList(pending)
                    pending.clear()
                    _state.update { it.copy(messages = (it.messages + batch).takeLast(MAX_MESSAGES)) }
                }
        }

        companion object {
            const val FLUSH_WINDOW_MS = 100L

            /** Older messages are dropped; nobody scrolls back through thousands of lines. */
            const val MAX_MESSAGES = 250
        }
    }
