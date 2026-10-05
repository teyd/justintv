package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Raised for every playback pipeline failure the UI needs to distinguish. */
sealed class PlaybackException(message: String) : Exception(message) {
    /** The channel is offline, banned, or does not exist. */
    class ChannelUnavailable(message: String) : PlaybackException(message)

    /** Twitch rejected the request, for example because a persisted query hash expired. */
    class RequestRejected(message: String) : PlaybackException(message)

    /** The device could not reach Twitch. */
    class Network(message: String) : PlaybackException(message)

    /** A playlist was fetched but is not usable HLS. */
    class InvalidPlaylist(message: String) : PlaybackException(message)
}

/**
 * Anonymous Twitch playback API: access tokens, playlist fetching, and the usher URL builder
 * that connects them.
 *
 * No user credentials are ever sent from this class. It is intentionally the only place that
 * talks to `gql.twitch.tv` and `usher.ttvnw.net` so the anonymous guarantee stays auditable.
 */
class TwitchPlaybackApi(
    private val httpClient: OkHttpClient,
    private val clientId: String = TwitchEndpoints.WEB_CLIENT_ID,
    private val gqlUrl: String = TwitchEndpoints.GQL_URL,
) : PlaylistFetcher, PlaybackTokenSource {
    private val contentType = "application/json".toMediaType()

    /**
     * Requests a stream playback access token.
     *
     * @param playerType one of `site`, `popout`, `mobile_web`, `embed`. Twitch serves
     * different ad loads per player type, which is what the swap strategy exploits.
     */
    override suspend fun playbackAccessToken(
        login: String,
        playerType: String,
    ): PlaybackAccessToken {
        val persisted = postGql(GqlRequestBuilder.persistedAccessTokenRequest(login, playerType))
        val first = PlaybackTokenParser.parse(persisted)
        if (first is PlaybackTokenParser.ParseResult.Success) return first.token

        val full = postGql(GqlRequestBuilder.fullAccessTokenRequest(login, playerType))
        return when (val second = PlaybackTokenParser.parse(full)) {
            is PlaybackTokenParser.ParseResult.Success -> second.token
            is PlaybackTokenParser.ParseResult.Failure -> throw second.reason.toException(second.message)
        }
    }

    /** Fetches a playlist (or any small text resource). Used to probe candidate streams. */
    override suspend fun fetchPlaylist(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", TwitchEndpoints.USER_AGENT)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    !response.isSuccessful -> throw PlaybackException.RequestRejected(
                        "HTTP ${response.code} for ${url.substringBefore('?')}",
                    )

                    body.isBlank() -> throw PlaybackException.InvalidPlaylist("empty response")
                    else -> body
                }
            }
        } catch (e: IOException) {
            throw PlaybackException.Network(e.message ?: "request failed")
        }
    }

    /** Convenience for callers that want the direct (unproxied) URL for a player type. */
    override suspend fun directStreamUrl(login: String, playerType: String): String {
        val token = playbackAccessToken(login, playerType)
        return UsherUrlBuilder.streamUrl(login, token, platform = "web")
    }

    private suspend fun postGql(body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(gqlUrl)
            .header("Client-Id", clientId)
            .header("Device-Id", UUID.randomUUID().toString().replace("-", ""))
            .header("User-Agent", TwitchEndpoints.USER_AGENT)
            .post(body.toRequestBody(contentType))
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful && text.isBlank()) {
                    throw PlaybackException.RequestRejected("HTTP ${response.code} from GraphQL")
                }
                text
            }
        } catch (e: IOException) {
            throw PlaybackException.Network(e.message ?: "GraphQL request failed")
        }
    }

    private fun PlaybackTokenParser.FailureReason.toException(message: String): PlaybackException =
        when (this) {
            PlaybackTokenParser.FailureReason.OfflineOrUnknownChannel ->
                PlaybackException.ChannelUnavailable("Channel is offline or does not exist")

            PlaybackTokenParser.FailureReason.GraphQlError ->
                PlaybackException.RequestRejected(message)

            PlaybackTokenParser.FailureReason.Malformed ->
                PlaybackException.RequestRejected("Unreadable GraphQL response: $message")
        }

}
