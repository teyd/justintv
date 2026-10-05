package dev.teyd.justintv.core.player

import androidx.media3.exoplayer.ExoPlayer

/**
 * Thin wrapper so UI code does not depend on ExoPlayer directly.
 *
 * The holder exists to keep the door open for a MediaSessionService-backed player in M5
 * without touching screen code: only this type changes.
 */
class PlayerHolder(
    val exoPlayer: ExoPlayer,
) {
    fun play(url: String) {
        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
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
