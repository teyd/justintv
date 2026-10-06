package dev.teyd.justintv.feature.watch

import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.PlaybackMethod
import dev.teyd.justintv.core.adfree.PlaylistResolver
import dev.teyd.justintv.core.adfree.ResolvedPlayback
import dev.teyd.justintv.core.data.AdBlockSettingsStore
import dev.teyd.justintv.core.network.DirectorySource
import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.player.ManifestAdDetector
import dev.teyd.justintv.core.player.PlaybackGate
import dev.teyd.justintv.core.player.PlaybackState
import dev.teyd.justintv.core.player.PlayerFactory
import dev.teyd.justintv.core.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

const val WATCH_ARG_LOGIN = "login"

private const val LIVE_STATS_REFRESH_MS = 30_000L

/** How the player is shown. Hidden means nothing is playing. */
enum class PlayerChrome { Hidden, Expanded, Mini }

/** Minutes offered for the sleep timer. Null in the UI means off. */
val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90, 120)

/** Remaining time for the sleep-timer button, `m:ss` or `h:mm:ss`. */
fun formatSleepRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

data class WatchUiState(
    val channelLogin: String = "",
    val displayName: String = "",
    val title: String = "",
    /** Shown over the video until the first frame, so the slot is never a black box. */
    val previewUrl: String? = null,
    /** Current viewers, or null until known. Refreshed while the stream plays. */
    val viewers: Int? = null,
    /** ISO-8601 time the stream went live. Uptime is derived from it. */
    val startedAt: String? = null,
    /** Landscape chat column. The overlay sizes the video to leave this room. */
    val landscapeChat: Boolean = false,
    val isLoading: Boolean = true,
    val status: String = "",
    /** Full description, for example "Proxy · eu2.luminous.dev". */
    val method: String = "",
    /** Compact form for the player overlay, for example "eu2.luminous.dev". */
    val source: String = "",
    /** Proxy host when one is in use, otherwise empty. The stats overlay prints "none". */
    val proxy: String = "",
    val isVerified: Boolean = false,
    val adBreakDetected: Boolean = false,
    val error: String? = null,
    val excludedProxies: Set<String> = emptySet(),
)

/**
 * Owns the player for the whole activity, not one screen.
 *
 * Leaving the watch screen docks the same video surface at the bottom instead of stopping.
 * [close] is what actually stops playback. Scope this view model to the activity, or the
 * player dies with the watch destination.
 */
@HiltViewModel
class WatchViewModel
    @Inject
    constructor(
        private val resolver: PlaylistResolver,
        private val adBlockSettings: AdBlockSettingsStore,
        private val directory: DirectorySource,
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

        private var currentMethod: PlaybackMethod? = null

        /** The in-flight source resolution; starting a new one supersedes it. */
        private var resolveJob: Job? = null
        private var sleepJob: Job? = null
        private var liveJob: Job? = null

        private val _sleepEndsAt = MutableStateFlow<Long?>(null)

        /** Epoch millis when the sleep timer stops playback, or null when it is off. */
        val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt.asStateFlow()
        private var adBreakHandled = false
        private var lastSourceSwitchAtMs = 0L
        private var errorRetries = 0

        private val playerListener =
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    onPlayerTimelineChanged()
                }
            }

        init {
            playbackGate.holder = playerHolder
            playerHolder.exoPlayer.addListener(playerListener)
            viewModelScope.launch {
                playerHolder.playback.collect(::onPlaybackState)
            }
            if (login.isNotBlank()) {
                resolveAndPlay()
                followLiveStats(login)
            }
        }

        /**
         * Starts or returns to [channel]. Same channel keeps the current stream.
         *
         * [displayName] and [title] are what the dock shows. A later open of the same channel
         * that omits them keeps the names already stored, so the watch route can call this with
         * only the login from its argument.
         */
        fun open(
            channel: String,
            displayName: String = "",
            title: String = "",
            previewUrl: String? = null,
            viewers: Int? = null,
            startedAt: String? = null,
        ) {
            _chrome.value = PlayerChrome.Expanded
            val sameStream = channel == login && _state.value.method.isNotEmpty()
            val name = displayName.ifBlank { if (sameStream) _state.value.displayName else channel }
            val streamTitle = title.ifBlank { if (sameStream) _state.value.title else "" }
            if (channel.isBlank() || sameStream) {
                _state.update { it.copy(displayName = name.ifBlank { channel }, title = streamTitle) }
                return
            }
            login = channel
            errorRetries = 0
            adBreakHandled = false
            _state.value =
                WatchUiState(
                    channelLogin = channel,
                    displayName = name.ifBlank { channel },
                    title = streamTitle,
                    previewUrl = previewUrl,
                    viewers = viewers,
                    startedAt = startedAt,
                )
            resolveAndPlay()
            followLiveStats(channel)
        }

        /**
         * Keeps viewers and uptime current. The directory numbers are a snapshot from when the
         * list loaded; this asks again, and fills them in when the stream was opened from a link.
         */
        private fun followLiveStats(channel: String) {
            liveJob?.cancel()
            liveJob =
                viewModelScope.launch {
                    while (true) {
                        try {
                            val live = directory.channelLive(channel)
                            if (live != null) {
                                _state.update { current ->
                                    if (current.channelLogin != channel) {
                                        current
                                    } else {
                                        current.copy(
                                            viewers = live.viewers,
                                            startedAt = live.startedAt ?: current.startedAt,
                                        )
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // A missed refresh keeps the last numbers.
                        }
                        delay(LIVE_STATS_REFRESH_MS)
                    }
                }
        }

        fun minimize() {
            if (_state.value.channelLogin.isBlank()) return
            _chrome.value = PlayerChrome.Mini
        }

        fun expand() {
            if (_state.value.channelLogin.isBlank()) return
            _chrome.value = PlayerChrome.Expanded
        }

        fun setLandscapeChat(visible: Boolean) {
            _state.update { it.copy(landscapeChat = visible) }
        }

        /**
         * Stops playback after [minutes]. Null or zero clears it. The timer keeps running if the
         * player is minimised; [close] is what it does when it fires, so the screen can sleep.
         */
        fun setSleepTimer(minutes: Int?) {
            sleepJob?.cancel()
            sleepJob = null
            if (minutes == null || minutes <= 0) {
                _sleepEndsAt.value = null
                return
            }
            val endsAt = System.currentTimeMillis() + minutes * 60_000L
            _sleepEndsAt.value = endsAt
            sleepJob =
                viewModelScope.launch {
                    delay(endsAt - System.currentTimeMillis())
                    close()
                }
        }

        /** Stops playback and removes the mini player. */
        fun close() {
            sleepJob?.cancel()
            sleepJob = null
            _sleepEndsAt.value = null
            resolveJob?.cancel()
            liveJob?.cancel()
            liveJob = null
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
            resolveJob =
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true, error = null, adBreakDetected = false) }
                    val adBlockEnabled = adBlockSettings.adBlockEnabled.first()
                    val disabledProxies = adBlockSettings.disabledProxies.first()
                    try {
                        val resolved: ResolvedPlayback =
                            withContext(Dispatchers.IO) {
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
                                proxy = (resolved.method as? PlaybackMethod.Proxied)?.proxyHost.orEmpty(),
                                isVerified = resolved.verified,
                                status =
                                    when {
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
        @OptIn(UnstableApi::class)
        private fun onPlayerTimelineChanged() {
            val manifest = playerHolder.exoPlayer.currentManifest as? HlsManifest
            val inAd =
                ManifestAdDetector.isAdAtLiveEdge(manifest) ||
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
