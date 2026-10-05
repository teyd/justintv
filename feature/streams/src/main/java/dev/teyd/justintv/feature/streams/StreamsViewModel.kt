package dev.teyd.justintv.feature.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.DefaultProxies
import dev.teyd.justintv.core.adfree.ProxyEndpoint
import dev.teyd.justintv.core.adfree.ProxyHealthChecker
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProxyStatus(
    val proxy: ProxyEndpoint,
    val online: Boolean? = null,
)

data class StreamsUiState(
    val proxyStatuses: List<ProxyStatus> = DefaultProxies.ALL.map { ProxyStatus(it) },
    val isCheckingProxies: Boolean = false,
)

/**
 * Backs the M1 entry screen: open a channel, and see which ad-free proxies are reachable.
 *
 * M3 replaces this screen with the real browse and follows lists; the proxy status list is
 * kept because it belongs in settings (M6).
 */
@HiltViewModel
class StreamsViewModel @Inject constructor(
    private val healthChecker: ProxyHealthChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    init {
        checkProxies()
    }

    fun checkProxies() {
        if (_state.value.isCheckingProxies) return
        viewModelScope.launch {
            _state.update { it.copy(isCheckingProxies = true) }
            val statuses = _state.value.proxyStatuses.map { status ->
                status.copy(online = null)
            }
            _state.update { it.copy(proxyStatuses = statuses) }

            // Check sequentially: a handful of hosts, and sequential probes avoid bursts.
            val updated = _state.value.proxyStatuses.map { status ->
                status.copy(online = healthChecker.isOnline(status.proxy))
            }
            _state.update { it.copy(proxyStatuses = updated, isCheckingProxies = false) }
        }
    }
}
