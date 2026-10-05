package dev.teyd.justintv.feature.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.hls.HlsManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.PlaybackMethod
import dev.teyd.justintv.core.adfree.PlaylistResolver
import dev.teyd.justintv.core.adfree.ResolvedPlayback
import dev.teyd.justintv.core.data.AdBlockSettingsStore
import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.player.ManifestAdDetector
import dev.teyd.justintv.core.player.PlaybackState
import dev.teyd.justintv.core.player.PlaybackGate
import dev.teyd.justintv.core.player.PlayerFactory
import dev.teyd.justintv.core.player.PlayerHolder
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val WATCH_ARG_LOGIN = "login"

/** How the player is shown. Hidden means nothing is playing. */
enum class PlayerChrome { Hidden, Expanded, Mini }

/** Which bottom corner a released mini player snaps to. */
enum class MiniSide { Left, Right }

/** Release speed (px/s) above which a fling decides the side, regardless of position. */
internal const val MINI_FLING_VELOCITY = 1_000f

/**
 * A release in the left half snaps left, the right half right; a fast fling wins over the
 * release point so dragging feels like flicking the card toward a corner.
 */
fun snapMiniSide(releaseX: Float, containerWidth: Float, velocityX: Float = 0f): MiniSide = when {
    velocityX <= -MINI_FLING_VELOCITY -> MiniSide.Left
    velocityX >= MINI_FLING_VELOCITY -> MiniSide.Right
    releaseX < containerWidth / 2f -> MiniSide.Left
    else -> MiniSide.Right
}

/** Vertical travel (fraction of the card height) that dismisses the mini player on release. */
internal const val MINI_DISMISS_FRACTION = 0.33f

/** Downward release speed (px/s) that dismisses even if the card was not dragged far. */
internal const val MINI_DISMISS_VELOCITY = 1_000f

/** True when a vertical release should dismiss: dragged far enough down, or flicked down. */
fun shouldDismissMini(offsetY: Float, cardHeight: Float, velocityY: Float): Boolean =
    offsetY >= cardHeight * MINI_DISMISS_FRACTION || velocityY >= MINI_DISMISS_VELOCITY

data class WatchUiState(
    val channelLogin: String = "",
    val isLoading: Boolean = true,
    val status: String = "",
    /** Full description, for example "Proxy · eu2.luminous.dev". */
    val method: String = "",
    /** Compact form for the player overlay, for example "eu2.luminous.dev". */
    val source: String = "",
    val isVerified: Boolean = false,
    val adBreakDetected: Boolean = false,
    val error: String? = null,
    val excludedProxies: Set<String> = emptySet(),
)

/**
 * Owns the player for the whole activity, not one screen.
 *
 * Leaving the watch screen minimises into a corner instead of stopping. [close] is what
 * actually stops playback. Scope this view model to the activity, or the player dies with
 * the watch destination.
 */
@HiltViewModel
class WatchViewModel @Inject constructor(
    private val resolver: PlaylistResolver,
    private val adBlockSettings: AdBlockSettingsStore,
    playerFactory: PlayerFactory,
    playbackGate: PlaybackGate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var login: String = savedStateHandle.get<String>(WATCH_ARG_LOGIN).orEmpty()

    val playerHolder: PlayerHolder = playerFactory.createHolder()

    private val _state = MutableStateFlow(WatchUiState(channelLogin = login))
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private val _chrome = MutableStateFlow(PlayerChrome.Hidden)
    val chrome: StateFlow<PlayerChrome> = _chrome.asStateFlow()

    private val _miniSide = MutableStateFlow(MiniSide.Right)
    val miniSide: StateFlow<MiniSide> = _miniSide.asStateFlow()

    private var currentMethod: PlaybackMethod? = null
    /** The in-flight source resolution; starting a new one supersedes it. */
    private var resolveJob: Job? = null
    private var adBreakHandled = false
    private var lastSourceSwitchAtMs = 0L
    private var errorRetries = 0

    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            onPlayerTimelineChanged()
        }
    }

    init {
        playbackGate.holder = playerHolder
        playerHolder.exoPlayer.addListener(playerListener)
        viewModelScope.launch {
            playerHolder.playback.collect(::onPlaybackState)
        }
        if (login.isNotBlank()) resolveAndPlay()
    }

    /** Starts or returns to [channel]. Same channel keeps the current stream. */
    fun open(channel: String) {
        _chrome.value = PlayerChrome.Expanded
        if (channel.isBlank() || channel == login && _state.value.method.isNotEmpty()) return
        login = channel
        errorRetries = 0
        adBreakHandled = false
        _state.value = WatchUiState(channelLogin = channel)
        resolveAndPlay()
    }

    fun minimize(side: MiniSide = _miniSide.value) {
        if (_state.value.channelLogin.isBlank()) return
        _miniSide.value = side
        _chrome.value = PlayerChrome.Mini
    }

    fun expand() {
        if (_state.value.channelLogin.isBlank()) return
        _chrome.value = PlayerChrome.Expanded
    }

    fun setMiniSide(side: MiniSide) {
        _miniSide.value = side
    }

    /** Stops playback and removes the mini player. */
    fun close() {
        resolveJob?.cancel()
        playerHolder.stop()
        login = ""
        currentMethod = null
        _state.value = WatchUiState()
        _chrome.value = PlayerChrome.Hidden
    }

    /** Re-resolves the stream, avoiding proxies that served ads or failed. */
    fun playAnotherSource() {
        // A manual retry: the current proxy is the one the viewer is unhappy with.
        (currentMethod as? PlaybackMethod.Proxied)?.proxyHost?.let { failed ->
            _state.update { it.copy(excludedProxies = it.excludedProxies + failed) }
        }
        errorRetries = 0
        resolveAndPlay()
    }

    private fun resolveAndPlay() {
        // Source probes take seconds; a newer resolve cancels the old one so a late result
        // cannot overwrite the channel the viewer switched to, or restart playback after close.
        resolveJob?.cancel()
        val requestedLogin = login
        resolveJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, adBreakDetected = false) }
            val adBlockEnabled = adBlockSettings.adBlockEnabled.first()
            val disabledProxies = adBlockSettings.disabledProxies.first()
            try {
                val resolved: ResolvedPlayback = withContext(Dispatchers.IO) {
                    resolver.resolve(
                        login = requestedLogin,
                        excluding = _state.value.excludedProxies,
                        adBlockEnabled = adBlockEnabled,
                        disabledProxies = disabledProxies,
                        onStatus = { message ->
                            _state.update { current ->
                                if (current.channelLogin == requestedLogin) {
                                    current.copy(status = message)
                                } else {
                                    current
                                }
                            }
                        },
                    )
                }
                if (login != requestedLogin) return@launch
                currentMethod = resolved.method
                _state.update {
                    it.copy(
                        isLoading = false,
                        method = resolved.method.label,
                        source = resolved.method.shortLabel,
                        isVerified = resolved.verified,
                        status = when {
                            !adBlockEnabled -> "Ad blocking is off"
                            resolved.verified -> "Ad-free stream verified"
                            else -> "No ad-free stream available right now"
                        },
                    )
                }
                playerHolder.play(resolved.playlistUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlaybackException) {
                if (login == requestedLogin) showError(e.message ?: "Could not start playback")
            } catch (e: Exception) {
                if (login == requestedLogin) showError(e.message ?: "Could not start playback")
            }
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(isLoading = false, error = message, status = "") }
    }

    /**
     * If the player fails (a proxy that returned a playlist and then stopped serving, say),
     * move on to another source automatically. A few attempts, then show the error.
     */
    private fun onPlaybackState(playback: PlaybackState) {
        if (playback.isPlaying) {
            errorRetries = 0
            return
        }
        if (playback.error == null || _state.value.isLoading) return

        if (errorRetries >= MAX_AUTOMATIC_RETRIES) {
            showError("Playback failed (${playback.error})")
            return
        }
        errorRetries++
        (currentMethod as? PlaybackMethod.Proxied)?.proxyHost?.let { failed ->
            _state.update { it.copy(excludedProxies = it.excludedProxies + failed) }
        }
        resolveAndPlay()
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

    val currentLogin: String get() = login

    companion object {
        /** Mirrors the reload cooldown used by the browser scripts: no cascading restarts. */
        const val SOURCE_SWITCH_COOLDOWN_MS = 30_000L

        const val MAX_AUTOMATIC_RETRIES = 3
    }
}
