package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import okhttp3.OkHttpClient

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
 * No user credentials are ever sent from this class.
 */
class TwitchPlaybackApi(
    httpClient: OkHttpClient,
    clientId: String = TwitchEndpoints.WEB_CLIENT_ID,
    gqlUrl: String = TwitchEndpoints.GQL_URL,
) : PlaylistFetcher, PlaybackTokenSource {

    private val gql = GqlClient(httpClient, clientId, gqlUrl)
    private val playlists = OkHttpPlaylistFetcher(httpClient)

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
        val persisted = gql.post(GqlRequestBuilder.persistedAccessTokenRequest(login, playerType))
        val first = PlaybackTokenParser.parse(persisted)
        if (first is PlaybackTokenParser.ParseResult.Success) return first.token

        val full = gql.post(GqlRequestBuilder.fullAccessTokenRequest(login, playerType))
        return when (val second = PlaybackTokenParser.parse(full)) {
            is PlaybackTokenParser.ParseResult.Success -> second.token
            is PlaybackTokenParser.ParseResult.Failure -> throw second.reason.toException(second.message)
        }
    }

    override suspend fun fetchPlaylist(url: String): String = playlists.fetchPlaylist(url)

    override suspend fun directStreamUrl(login: String, playerType: String): String {
        val token = playbackAccessToken(login, playerType)
        return UsherUrlBuilder.streamUrl(login, token, platform = "web")
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
