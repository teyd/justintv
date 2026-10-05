package dev.teyd.justintv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.DefaultProxies
import dev.teyd.justintv.core.adfree.ProxyEndpoint
import dev.teyd.justintv.core.adfree.ProxyHealthChecker
import dev.teyd.justintv.core.data.AdBlockSettingsStore
import dev.teyd.justintv.core.data.ChatSettingsStore
import dev.teyd.justintv.core.data.PlaybackSettingsStore
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProxyStatus(
    val proxy: ProxyEndpoint,
    /** null while the check is running. */
    val online: Boolean? = null,
    /** Whether the proxy may be used for playback. */
    val enabled: Boolean = true,
)

data class SettingsUiState(
    val backgroundPlayback: Boolean = false,
    val pictureInPicture: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val proxyStatuses: List<ProxyStatus> = DefaultProxies.ALL.map { ProxyStatus(it) },
    val isCheckingProxies: Boolean = false,
    val recentMessages: Boolean = true,
    val recentMessageLimit: Int = ChatSettingsStore.DEFAULT_RECENT_MESSAGE_LIMIT,
    val sevenTv: Boolean = true,
    val bttv: Boolean = true,
    val ffz: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val healthChecker: ProxyHealthChecker,
    private val playbackSettings: PlaybackSettingsStore,
    private val adBlockSettings: AdBlockSettingsStore,
    private val chatSettings: ChatSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playbackSettings.backgroundPlayback.collect { enabled ->
                _state.update { it.copy(backgroundPlayback = enabled) }
            }
        }
        viewModelScope.launch {
            playbackSettings.pictureInPicture.collect { enabled ->
                _state.update { it.copy(pictureInPicture = enabled) }
            }
        }
        viewModelScope.launch {
            adBlockSettings.adBlockEnabled.collect { enabled ->
                _state.update { it.copy(adBlockEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            adBlockSettings.disabledProxies.collect { disabled ->
                _state.update { state ->
                    state.copy(
                        proxyStatuses = state.proxyStatuses.map {
                            it.copy(enabled = it.proxy.host !in disabled)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            chatSettings.recentMessages.collect { enabled ->
                _state.update { it.copy(recentMessages = enabled) }
            }
        }
        viewModelScope.launch {
            chatSettings.recentMessageLimit.collect { limit ->
                _state.update { it.copy(recentMessageLimit = limit) }
            }
        }
        viewModelScope.launch {
            combine(chatSettings.sevenTv, chatSettings.bttv, chatSettings.ffz) { seven, bttv, ffz ->
                Triple(seven, bttv, ffz)
            }.collect { (seven, bttv, ffz) ->
                _state.update { it.copy(sevenTv = seven, bttv = bttv, ffz = ffz) }
            }
        }
        checkProxies()
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { playbackSettings.setBackgroundPlayback(enabled) }
    }

    fun setPictureInPicture(enabled: Boolean) {
        viewModelScope.launch { playbackSettings.setPictureInPicture(enabled) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { adBlockSettings.setAdBlockEnabled(enabled) }
    }

    fun setProxyEnabled(proxy: ProxyEndpoint, enabled: Boolean) {
        viewModelScope.launch { adBlockSettings.setProxyEnabled(proxy.host, enabled) }
    }

    fun setRecentMessages(enabled: Boolean) {
        viewModelScope.launch { chatSettings.setRecentMessages(enabled) }
    }

    fun setRecentMessageLimit(limit: Int) {
        viewModelScope.launch { chatSettings.setRecentMessageLimit(limit) }
    }

    fun setSevenTv(enabled: Boolean) {
        viewModelScope.launch { chatSettings.setSevenTv(enabled) }
    }

    fun setBttv(enabled: Boolean) {
        viewModelScope.launch { chatSettings.setBttv(enabled) }
    }

    fun setFfz(enabled: Boolean) {
        viewModelScope.launch { chatSettings.setFfz(enabled) }
    }

    /**
     * Checks every proxy at once. Each result appears as soon as it arrives, so one hanging
     * host cannot hold up the others. The health checker's own timeout bounds the slowest.
     */
    fun checkProxies() {
        if (_state.value.isCheckingProxies) return
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    isCheckingProxies = true,
                    proxyStatuses = current.proxyStatuses.map { it.copy(online = null) },
                )
            }
            coroutineScope {
                _state.value.proxyStatuses.forEach { status ->
                    launch {
                        val online = healthChecker.isOnline(status.proxy)
                        _state.update { current ->
                            current.copy(
                                proxyStatuses = current.proxyStatuses.map {
                                    if (it.proxy == status.proxy) it.copy(online = online) else it
                                },
                            )
                        }
                    }
                }
            }
            _state.update { it.copy(isCheckingProxies = false) }
        }
    }

    companion object {
        /** Offered history sizes; small enough to read, big enough to fill a chat pane. */
        val MESSAGE_LIMIT_OPTIONS = listOf(20, 50, 80, 150)
    }
}
