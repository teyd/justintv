package dev.teyd.justintv.core.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pauses playback when the app leaves the screen, unless the viewer turned background play on.
 *
 * Returning to the app resumes only if this class was the one that paused. The mini player
 * itself stays inside the app; this gate is what stops audio when you switch away.
 */
@Singleton
class PlaybackGate
    @Inject
    constructor() {
        @Volatile
        var holder: PlayerHolder? = null

        @Volatile
        var allowBackground: Boolean = false

        private var pausedForBackground = false

        fun onBackground() {
            val player = holder ?: return
            if (allowBackground || !player.exoPlayer.isPlaying) return
            player.pause()
            pausedForBackground = true
        }

        fun onForeground() {
            if (!pausedForBackground) return
            pausedForBackground = false
            holder?.resume()
        }
    }
