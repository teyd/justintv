package dev.teyd.justintv.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import dev.teyd.justintv.core.network.TwitchEndpoints
import okhttp3.OkHttpClient

/**
 * Builds players and HLS media sources.
 *
 * One OkHttp client is shared with the rest of the app so video segment traffic reuses the
 * same connection pool as API calls.
 */
class PlayerFactory(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    /**
     * Live video does not need ExoPlayer's VOD-sized defaults, which wait for 2.5 s of media
     * before the first frame. One second starts sooner and still rides out a normal live edge.
     */
    fun createPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000,
            )
            .build()
        return ExoPlayer.Builder(context).setLoadControl(loadControl).build()
    }

    fun createHolder(): PlayerHolder = PlayerHolder(createPlayer(), ::hlsMediaSource)

    /**
     * An HLS source for [playlistUrl].
     *
     * The MIME type is set explicitly because proxy playlist URLs have no `.m3u8` extension.
     */
    fun hlsMediaSource(playlistUrl: String): MediaSource {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(httpClient)
                .setUserAgent(TwitchEndpoints.USER_AGENT),
        )
        val mediaItem = MediaItem.Builder()
            .setUri(playlistUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        return HlsMediaSource.Factory(dataSourceFactory)
            // Prepare from the master playlist alone. Twitch lists CODECS on every variant, so
            // the player does not need to download a media playlist before it can start.
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
    }
}
