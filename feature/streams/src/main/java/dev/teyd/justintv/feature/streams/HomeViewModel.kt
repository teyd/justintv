package dev.teyd.justintv.feature.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.data.LanguageFilterStore
import dev.teyd.justintv.core.model.Game
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.network.AuthState
import dev.teyd.justintv.core.network.DirectorySource
import dev.teyd.justintv.core.network.TwitchSession
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One list on screen: its items, whether it is loading, and why it failed if it did. */
data class LoadState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    val languages: Set<String> = emptySet(),
    val live: LoadState<LiveStream> = LoadState(isLoading = true),
    val games: LoadState<Game> = LoadState(isLoading = true),
    val following: LoadState<LiveStream> = LoadState(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val directory: DirectorySource,
    private val languageStore: LanguageFilterStore,
    private val session: TwitchSession,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var liveJob: Job? = null
    private var followingJob: Job? = null

    init {
        viewModelScope.launch {
            session.state.collect { auth ->
                val loggedIn = auth is AuthState.LoggedIn
                _state.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn) loadFollowing() else followingJob?.cancel()
            }
        }
        viewModelScope.launch {
            languageStore.languages.distinctUntilChanged().collect { languages ->
                _state.update { it.copy(languages = languages) }
                loadLive(languages)
            }
        }
        loadGames()
    }

    fun refreshLive() = loadLive(_state.value.languages)

    fun refreshFollowing() = loadFollowing()

    fun refreshGames() = loadGames()

    fun setLanguages(languages: Set<String>) {
        viewModelScope.launch { languageStore.set(languages) }
    }

    private fun loadLive(languages: Set<String>) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            _state.update { it.copy(live = it.live.copy(isLoading = true, error = null)) }
            try {
                val streams = directory.topStreams(languages)
                _state.update { it.copy(live = LoadState(items = streams)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(live = it.live.copy(isLoading = false, error = e.message ?: "Could not load streams"))
                }
            }
        }
    }

    private fun loadFollowing() {
        followingJob?.cancel()
        followingJob = viewModelScope.launch {
            _state.update { it.copy(following = it.following.copy(isLoading = true, error = null)) }
            try {
                val streams = session.followedStreams()
                _state.update { it.copy(following = LoadState(items = streams)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(following = it.following.copy(isLoading = false, error = e.message ?: "Could not load follows"))
                }
            }
        }
    }

    private fun loadGames() {
        viewModelScope.launch {
            _state.update { it.copy(games = it.games.copy(isLoading = true, error = null)) }
            try {
                val games = directory.topGames()
                _state.update { it.copy(games = LoadState(items = games)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(games = it.games.copy(isLoading = false, error = e.message ?: "Could not load categories"))
                }
            }
        }
    }
}
