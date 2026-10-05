package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.network.model.PlaybackAccessToken

/** Player types Twitch serves streams for. Ad load differs between them. */
object PlayerTypes {
    const val SITE = "site"
    const val POPOUT = "popout"
    const val MOBILE_WEB = "mobile_web"
    const val EMBED = "embed"

    /** Player types probed by the ad-free swap fallback, in order. */
    val SWAP_ORDER = listOf(POPOUT, MOBILE_WEB, EMBED)
}

/**
 * Supplies anonymous playback credentials and direct stream URLs.
 *
 * Extracted from [TwitchPlaybackApi] so the ad-free resolver can be unit tested with fakes.
 */
interface PlaybackTokenSource {
    suspend fun playbackAccessToken(
        login: String,
        playerType: String = PlayerTypes.SITE,
    ): PlaybackAccessToken

    suspend fun directStreamUrl(
        login: String,
        playerType: String = PlayerTypes.SITE,
    ): String
}
