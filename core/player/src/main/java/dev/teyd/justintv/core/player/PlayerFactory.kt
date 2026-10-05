package dev.teyd.justintv.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
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
    fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context).build()

    fun hlsMediaSource(playlistUrl: String): MediaSource {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(httpClient)
                .setUserAgent(TwitchEndpoints.USER_AGENT),
        )
        return HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(playlistUrl))
    }
}
