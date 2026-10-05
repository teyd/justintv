package dev.teyd.justintv.feature.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.chat.ChatSession
import dev.teyd.justintv.core.chat.ChatStatus
import dev.teyd.justintv.core.model.ChatMessage
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: ChatStatus = ChatStatus.Connecting,
)

/**
 * Chat for the channel being watched.
 *
 * Incoming messages are collected for a short window and published together. A busy chat can
 * deliver dozens of lines a second, and one list update per line would make the screen stutter.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val session: ChatSession,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val login: String = checkNotNull(savedStateHandle[WATCH_ARG_LOGIN]) {
        "Missing $WATCH_ARG_LOGIN navigation argument"
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val pending = ArrayList<ChatMessage>()
    private var flushJob: Job? = null

    init {
        viewModelScope.launch {
            session.connection.collect { connection ->
                _state.update { it.copy(status = connection.status) }
            }
        }
        viewModelScope.launch {
            session.messages(login).collect { message ->
                pending += message
                scheduleFlush()
            }
        }
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
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
