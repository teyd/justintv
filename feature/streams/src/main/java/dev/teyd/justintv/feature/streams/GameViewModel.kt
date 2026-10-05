package dev.teyd.justintv.feature.streams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.data.LanguageFilterStore
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.network.DirectorySource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

const val GAME_ARG_NAME = "name"

data class GameUiState(
    val gameName: String = "",
    val languages: Set<String> = emptySet(),
    val streams: LoadState<LiveStream> = LoadState(isLoading = true),
)

/** Live streams in one category, filtered by the same language setting as the home screen. */
@HiltViewModel
class GameViewModel
    @Inject
    constructor(
        private val directory: DirectorySource,
        private val languageStore: LanguageFilterStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val gameName: String =
            checkNotNull(savedStateHandle[GAME_ARG_NAME]) {
                "Missing $GAME_ARG_NAME navigation argument"
            }

        private val _state = MutableStateFlow(GameUiState(gameName = gameName))
        val state: StateFlow<GameUiState> = _state.asStateFlow()

        private var loadJob: Job? = null

        init {
            viewModelScope.launch {
                languageStore.languages.distinctUntilChanged().collect { languages ->
                    _state.update { it.copy(languages = languages) }
                    load(languages)
                }
            }
        }

        fun refresh() = load(_state.value.languages)

        fun setLanguages(languages: Set<String>) {
            viewModelScope.launch { languageStore.set(languages) }
        }

        private fun load(languages: Set<String>) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _state.update { it.copy(streams = it.streams.copy(isLoading = true, error = null)) }
                    try {
                        val streams = directory.gameStreams(gameName, languages)
                        _state.update { it.copy(streams = LoadState(items = streams)) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(streams = it.streams.copy(isLoading = false, error = e.message ?: "Could not load streams"))
                        }
                    }
                }
        }
    }
