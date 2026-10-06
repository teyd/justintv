package dev.teyd.justintv.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Posts JSON bodies, for APIs that have no GET form (the 7TV GraphQL endpoint). Implemented by
 * [OkHttpJsonPoster] for production and by fakes in tests.
 */
fun interface JsonPoster {
    suspend fun postJson(
        url: String,
        body: String,
    ): String
}

/** Posts with cancellable calls and the app's shared OkHttp client. */
class OkHttpJsonPoster(
    private val client: OkHttpClient,
) : JsonPoster {
    override suspend fun postJson(
        url: String,
        body: String,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", TwitchEndpoints.USER_AGENT)
                .post(body.toRequestBody(JSON))
                .build()
        val response =
            try {
                client.newCall(request).awaitResponse()
            } catch (e: IOException) {
                throw PlaybackException.Network(e.message ?: "request failed")
            }
        return response.use {
            val text =
                try {
                    withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    throw PlaybackException.Network(e.message ?: "read failed")
                }
            if (!it.isSuccessful) {
                throw PlaybackException.RequestRejected("HTTP ${it.code} for ${url.substringBefore('?')}")
            }
            text
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
