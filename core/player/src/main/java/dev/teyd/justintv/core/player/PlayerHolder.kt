package dev.teyd.justintv.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** What the UI needs to draw play/pause, a spinner and an error. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
)

/** One selectable video rendition. */
data class VideoQuality(
    val label: String,
    val height: Int,
    val frameRate: Float,
    val bitrate: Int,
    internal val group: Tracks.Group,
    internal val trackIndex: Int,
)

/** A snapshot for the stats overlay. Unknown values are null. */
data class PlayerStats(
    val liveLatencyMs: Long? = null,
    val bufferedMs: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val codecs: String? = null,
    val bandwidthBps: Long? = null,
    val droppedFrames: Int = 0,
    val playbackSpeed: Float = 1f,
)

/**
 * Thin wrapper so UI code does not depend on ExoPlayer directly.
 *
 * Streams are always played through [mediaSourceFor], which builds an explicit HLS source.
 * Handing ExoPlayer a bare URL does not work for proxy playlists: their URLs do not end in
 * `.m3u8`, so ExoPlayer cannot infer the format and fails with an unrecognised input error.
 *
 * The holder also keeps the door open for a MediaSessionService-backed player in M5 without
 * touching screen code: only this type changes.
 */
class PlayerHolder(
    val exoPlayer: ExoPlayer,
    private val mediaSourceFor: (String) -> MediaSource,
) {
    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _qualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val qualities: StateFlow<List<VideoQuality>> = _qualities.asStateFlow()

    /** The quality the viewer chose, or null for automatic selection. */
    private val _selectedQuality = MutableStateFlow<VideoQuality?>(null)
    val selectedQuality: StateFlow<VideoQuality?> = _selectedQuality.asStateFlow()

    /** Written from the playback and analytics threads, read from the UI thread. */
    private val droppedFrameCount = AtomicInteger()

    @Volatile
    private var bandwidthBps: Long? = null

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playback.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playback.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playback.update { it.copy(error = error.errorCodeName) }
            }

            override fun onTracksChanged(tracks: Tracks) {
                _qualities.value = qualitiesOf(tracks)
            }
        }

    private val analytics =
        object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                droppedFrameCount.addAndGet(droppedFrames)
            }

            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                bandwidthBps = bitrateEstimate
            }
        }

    init {
        exoPlayer.addListener(listener)
        exoPlayer.addAnalyticsListener(analytics)
    }

    fun play(url: String) {
        droppedFrameCount.set(0)
        _playback.update { PlaybackState(isBuffering = true) }
        exoPlayer.setMediaSource(mediaSourceFor(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun pause() = exoPlayer.pause()

    fun resume() = exoPlayer.play()

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else resume()
    }

    /** Picks a rendition, or automatic selection when [quality] is null. */
    fun selectQuality(quality: VideoQuality?) {
        val parameters = exoPlayer.trackSelectionParameters.buildUpon()
        if (quality == null) {
            parameters.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            parameters.setOverrideForType(
                TrackSelectionOverride(quality.group.mediaTrackGroup, quality.trackIndex),
            )
        }
        exoPlayer.trackSelectionParameters = parameters.build()
        _selectedQuality.value = quality
    }

    fun readStats(): PlayerStats {
        val format = exoPlayer.videoFormat
        val offset = exoPlayer.currentLiveOffset
        return PlayerStats(
            liveLatencyMs = offset.takeIf { it != C.TIME_UNSET },
            bufferedMs = exoPlayer.totalBufferedDuration,
            width = format?.width?.takeIf { it > 0 },
            height = format?.height?.takeIf { it > 0 },
            frameRate = format?.frameRate?.takeIf { it > 0f },
            bitrate = format?.bitrate?.takeIf { it > 0 },
            codecs = format?.codecs,
            bandwidthBps = bandwidthBps,
            droppedFrames = droppedFrameCount.get(),
            playbackSpeed = exoPlayer.playbackParameters.speed,
        )
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.removeAnalyticsListener(analytics)
        exoPlayer.release()
    }

    private fun qualitiesOf(tracks: Tracks): List<VideoQuality> {
        val result = mutableListOf<VideoQuality>()
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                result +=
                    VideoQuality(
                        label = QualityLabel.of(format),
                        height = format.height.coerceAtLeast(0),
                        frameRate = format.frameRate.takeIf { it > 0f } ?: 0f,
                        bitrate = format.bitrate.coerceAtLeast(0),
                        group = group,
                        trackIndex = index,
                    )
            }
        }
        return result.sortedWith(compareByDescending<VideoQuality> { it.height }.thenByDescending { it.frameRate })
    }
}

/** "1080p60", "720p", or "Audio only" when a rendition has no picture. */
object QualityLabel {
    fun of(format: Format): String = of(format.height, format.frameRate)

    fun of(
        height: Int,
        frameRate: Float,
    ): String {
        if (height <= 0) return "Audio only"
        // Twitch uses 30 and 60; anything above 45 is the 60fps rendition.
        return if (frameRate >= 45f) "${height}p${Math.round(frameRate / 10f) * 10}" else "${height}p"
    }
}
