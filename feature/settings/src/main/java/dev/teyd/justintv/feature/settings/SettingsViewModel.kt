package dev.teyd.justintv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.DefaultProxies
import dev.teyd.justintv.core.adfree.ProxyEndpoint
import dev.teyd.justintv.core.adfree.ProxyHealthChecker
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProxyStatus(
    val proxy: ProxyEndpoint,
    /** null while the check is running. */
    val online: Boolean? = null,
)

data class SettingsUiState(
    val proxyStatuses: List<ProxyStatus> = DefaultProxies.ALL.map { ProxyStatus(it) },
    val isCheckingProxies: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val healthChecker: ProxyHealthChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        checkProxies()
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
}
