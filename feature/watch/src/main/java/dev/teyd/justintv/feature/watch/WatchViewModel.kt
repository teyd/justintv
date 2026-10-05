package dev.teyd.justintv.feature.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.PlaybackMethod
import dev.teyd.justintv.core.adfree.PlaylistResolver
import dev.teyd.justintv.core.adfree.ResolvedPlayback
import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.player.ManifestAdDetector
import dev.teyd.justintv.core.player.PlayerFactory
import dev.teyd.justintv.core.player.PlayerHolder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val WATCH_ARG_LOGIN = "login"

data class WatchUiState(
    val channelLogin: String = "",
    val isLoading: Boolean = true,
    val status: String = "",
    val method: String = "",
    val isVerified: Boolean = false,
    val adBreakDetected: Boolean = false,
    val error: String? = null,
    val excludedProxies: Set<String> = emptySet(),
)

/**
 * Owns the player and the ad-free resolution loop for one channel.
 *
 * The view model outlives configuration changes, so the ExoPlayer instance (and therefore
 * the stream) survives rotation without a service. Background playback arrives in M5.
 */
@HiltViewModel
class WatchViewModel @Inject constructor(
    private val resolver: PlaylistResolver,
    playerFactory: PlayerFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val login: String = checkNotNull(savedStateHandle[WATCH_ARG_LOGIN]) {
        "Missing $WATCH_ARG_LOGIN navigation argument"
    }

    val playerHolder: PlayerHolder = PlayerHolder(playerFactory.createPlayer())

    private val _state = MutableStateFlow(WatchUiState(channelLogin = login))
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private var currentMethod: PlaybackMethod? = null
    private var adBreakHandled = false
    private var lastSourceSwitchAtMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            onPlayerTimelineChanged()
        }
    }

    init {
        playerHolder.exoPlayer.addListener(playerListener)
        resolveAndPlay()
    }

    /** Re-resolves the stream, avoiding proxies that served ads or failed. */
    fun playAnotherSource() {
        _state.update { it.copy(excludedProxies = it.excludedProxies) }
        resolveAndPlay()
    }

    private fun resolveAndPlay() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, adBreakDetected = false) }
            try {
                val resolved: ResolvedPlayback = withContext(Dispatchers.IO) {
                    resolver.resolve(
                        login = login,
                        excluding = _state.value.excludedProxies,
                        onStatus = { message -> _state.update { it.copy(status = message) } },
                    )
                }
                currentMethod = resolved.method
                _state.update {
                    it.copy(
                        isLoading = false,
                        method = resolved.method.label,
                        isVerified = resolved.verified,
                        status = if (resolved.verified) {
                            "Ad-free stream verified"
                        } else {
                            "No ad-free stream available right now"
                        },
                    )
                }
                playerHolder.play(resolved.playlistUrl)
            } catch (e: PlaybackException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Could not start playback",
                        status = "",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Could not start playback",
                        status = "",
                    )
                }
            }
        }
    }

    /**
     * Called whenever the player's manifest changes. If the live edge is inside an ad and the
     * current source was a proxy, that proxy is dropped and playback restarts elsewhere.
     */
    private fun onPlayerTimelineChanged() {
        val manifest = playerHolder.exoPlayer.currentManifest as? HlsManifest
        val inAd = ManifestAdDetector.isAdAtLiveEdge(manifest) ||
            ManifestAdDetector.hasAdMarkers(manifest)

        if (!inAd) {
            adBreakHandled = false
            _state.update { it.copy(adBreakDetected = false) }
            return
        }

        _state.update { it.copy(adBreakDetected = true) }
        if (adBreakHandled) return

        val now = System.currentTimeMillis()
        if (now - lastSourceSwitchAtMs < SOURCE_SWITCH_COOLDOWN_MS) return

        val failedProxy = (currentMethod as? PlaybackMethod.Proxied)?.proxyHost
        if (failedProxy == null) {
            _state.update { it.copy(status = "Ad break in progress") }
            return
        }

        adBreakHandled = true
        lastSourceSwitchAtMs = now
        _state.update {
            it.copy(
                excludedProxies = it.excludedProxies + failedProxy,
                status = "Ads on $failedProxy, switching source…",
            )
        }
        resolveAndPlay()
    }

    override fun onCleared() {
        playerHolder.exoPlayer.removeListener(playerListener)
        playerHolder.release()
        super.onCleared()
    }

    companion object {
        /** Mirrors the reload cooldown used by the browser scripts: no cascading restarts. */
        const val SOURCE_SWITCH_COOLDOWN_MS = 30_000L
    }
}
