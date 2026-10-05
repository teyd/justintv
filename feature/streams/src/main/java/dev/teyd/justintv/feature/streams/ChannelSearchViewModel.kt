package dev.teyd.justintv.feature.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.network.DirectorySource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ChannelSearchUiState(
    val query: String = "",
    val hits: List<ChannelHit> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

/**
 * Autocomplete for the home search field.
 *
 * The field text is updated immediately. The directory call waits for a pause, and a
 * newer keystroke cancels the one still in flight, so a slow response cannot overwrite
 * a query the viewer has already moved on from.
 */
@HiltViewModel
class ChannelSearchViewModel
    @Inject
    constructor(
        private val directory: DirectorySource,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChannelSearchUiState())
        val state: StateFlow<ChannelSearchUiState> = _state.asStateFlow()

        private var searchJob: Job? = null

        fun onQueryChange(raw: String) {
            searchJob?.cancel()
            if (!ChannelSearch.shouldSearch(raw)) {
                _state.update {
                    it.copy(query = raw, hits = emptyList(), isSearching = false, error = null)
                }
                return
            }
            _state.update { it.copy(query = raw, isSearching = false, error = null) }
            val text = ChannelSearch.searchText(raw)
            searchJob =
                viewModelScope.launch {
                    delay(ChannelSearch.DEBOUNCE_MS)
                    _state.update { it.copy(isSearching = true) }
                    try {
                        val hits = directory.searchChannels(text)
                        _state.update { it.copy(hits = hits, isSearching = false) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(isSearching = false, error = e.message ?: "Could not search")
                        }
                    }
                }
        }

        fun clear() = onQueryChange("")
    }
