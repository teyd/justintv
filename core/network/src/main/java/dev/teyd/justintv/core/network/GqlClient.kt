package dev.teyd.justintv.core.network

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The one place that talks to `gql.twitch.tv`. Anonymous only: no user credentials are sent.
 */
class GqlClient(
    private val httpClient: OkHttpClient,
    private val clientId: String = TwitchEndpoints.WEB_CLIENT_ID,
    private val gqlUrl: String = TwitchEndpoints.GQL_URL,
) {
    private val contentType = "application/json".toMediaType()

    /** Posts a request body and returns the raw response text, even for GraphQL error payloads. */
    suspend fun post(body: String): String {
        val request = Request.Builder()
            .url(gqlUrl)
            .header("Client-Id", clientId)
            .header("Device-Id", UUID.randomUUID().toString().replace("-", ""))
            .header("User-Agent", TwitchEndpoints.USER_AGENT)
            .post(body.toRequestBody(contentType))
            .build()
        val response = try {
            httpClient.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw PlaybackException.Network(e.message ?: "GraphQL request failed")
        }
        return response.use {
            val text = try {
                withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw PlaybackException.Network(e.message ?: "GraphQL read failed")
            }
            if (!it.isSuccessful && text.isBlank()) {
                throw PlaybackException.RequestRejected("HTTP ${it.code} from GraphQL")
            }
            text
        }
    }
}
