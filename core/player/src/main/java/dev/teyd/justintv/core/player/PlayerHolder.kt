package dev.teyd.justintv.core.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource

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
    fun play(url: String) {
        exoPlayer.setMediaSource(mediaSourceFor(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun release() {
        exoPlayer.release()
    }
}
