package dev.teyd.justintv.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Fetches small text resources (playlists, ping endpoints) with cancellable calls.
 *
 * Probing code gets its own instance built on a client with short timeouts, so a proxy that
 * hangs costs a few seconds instead of half a minute.
 */
class OkHttpTextFetcher(
    private val client: OkHttpClient,
) : TextFetcher {
    override suspend fun fetchText(url: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", TwitchEndpoints.USER_AGENT)
                .build()
        val response =
            try {
                client.newCall(request).awaitResponse()
            } catch (e: IOException) {
                throw PlaybackException.Network(e.message ?: "request failed")
            }
        return response.use {
            val body =
                try {
                    withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    throw PlaybackException.Network(e.message ?: "read failed")
                }
            when {
                !it.isSuccessful -> throw PlaybackException.RequestRejected(
                    "HTTP ${it.code} for ${url.substringBefore('?')}",
                )

                body.isBlank() -> throw PlaybackException.InvalidPlaylist("empty response")

                else -> body
            }
        }
    }
}
