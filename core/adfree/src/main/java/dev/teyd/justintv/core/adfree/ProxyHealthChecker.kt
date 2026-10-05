package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.network.PlaylistFetcher

/** Checks whether a proxy is reachable, for the settings list and to order candidates. */
class ProxyHealthChecker(private val fetcher: PlaylistFetcher) {

    suspend fun isOnline(proxy: ProxyEndpoint): Boolean = try {
        val body = fetcher.fetchPlaylist(proxy.pingUrl())
        // Some proxies report {"online": false} while working, so any well-formed JSON counts.
        body.trimStart().startsWith("{") || body.contains("online")
    } catch (_: Exception) {
        false
    }
}
