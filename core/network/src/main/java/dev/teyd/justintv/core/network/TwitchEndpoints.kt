package dev.teyd.justintv.core.network

/**
 * Endpoints and identifiers used by the playback pipeline.
 *
 * [WEB_CLIENT_ID] is the public client ID of Twitch's own web player. It is used for
 * anonymous playback token requests, which never carry user credentials. Once login (M4)
 * lands, user calls use our own registered application client ID instead.
 */
object TwitchEndpoints {
    const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"

    const val GQL_URL = "https://gql.twitch.tv/gql"
    const val USHER_BASE = "https://usher.ttvnw.net/api/v2/channel/hls"

    /** Anonymous requests identify themselves as a web browser with a random device ID. */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; JustinTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
}
