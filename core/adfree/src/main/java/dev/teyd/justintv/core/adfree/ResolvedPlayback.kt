package dev.teyd.justintv.core.adfree

/** How a playlist URL was obtained. Surfaced in the UI so behaviour is never a mystery. */
sealed interface PlaybackMethod {
    /** Played as Twitch served it, no ad avoidance applied. */
    data object Direct : PlaybackMethod

    /** Resolved through an m3u8 proxy. */
    data class Proxied(val proxyHost: String) : PlaybackMethod

    /** Played with a different player type, which Twitch sometimes serves fewer ads for. */
    data class PlayerTypeSwap(val playerType: String) : PlaybackMethod

    val label: String
        get() = when (this) {
            Direct -> "Direct"
            is Proxied -> "Proxy · $proxyHost"
            is PlayerTypeSwap -> "Player type · $playerType"
        }
}

/**
 * A playable playlist URL plus how it was chosen.
 *
 * [verified] is true when the playlist was fetched and checked for ad markers before being
 * returned. The last-resort direct stream is unverified.
 */
data class ResolvedPlayback(
    val playlistUrl: String,
    val method: PlaybackMethod,
    val verified: Boolean,
)
