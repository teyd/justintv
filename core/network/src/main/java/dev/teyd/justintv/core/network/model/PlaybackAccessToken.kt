package dev.teyd.justintv.core.network.model

/**
 * A playback access token, as returned by the `PlaybackAccessToken` GraphQL operation.
 *
 * The pair is appended to usher requests as `sig` and `token` query parameters. The token
 * value is a JSON string with stream metadata that the proxy and the player both accept.
 */
data class PlaybackAccessToken(
    val value: String,
    val signature: String,
)
