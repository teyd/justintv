package dev.teyd.justintv.core.network

/**
 * Fetches text resources from the network. Implemented by [TwitchPlaybackApi] for production
 * and by fakes in tests, so the ad-free resolver can be exercised without a network.
 */
fun interface PlaylistFetcher {
    suspend fun fetchPlaylist(url: String): String
}
