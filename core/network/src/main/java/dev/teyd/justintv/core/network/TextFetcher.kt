package dev.teyd.justintv.core.network

/**
 * Fetches text resources (playlists, ping endpoints, emote JSON) from the network.
 * Implemented by [OkHttpTextFetcher] for production and by fakes in tests, so callers can be
 * exercised without a network.
 */
fun interface TextFetcher {
    suspend fun fetchText(url: String): String
}
