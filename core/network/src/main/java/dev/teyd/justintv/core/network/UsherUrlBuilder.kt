package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import java.net.URLEncoder

/**
 * Builds usher playback URLs.
 *
 * Twitch moved to Usher V2 (`/api/v2/channel/hls/...`). The query parameters below are the
 * ones the web player sends: source and audio-only variants, low latency mode, and a random
 * `p` value that discourages request caching.
 */
object UsherUrlBuilder {

    fun streamUrl(
        login: String,
        token: PlaybackAccessToken,
        platform: String = "web",
        supportedCodecs: String? = null,
        randomToken: Int = (0..9_999_999).random(),
    ): String = buildString {
        append(TwitchEndpoints.USHER_BASE)
        append('/')
        append(login.lowercase())
        append(".m3u8")
        append("?allow_source=true")
        append("&allow_audio_only=true")
        append("&fast_bread=true")
        append("&include_unavailable=true")
        append("&platform=").append(platform)
        append("&p=").append(randomToken)
        append("&sig=").append(encode(token.signature))
        append("&token=").append(encode(token.value))
        if (!supportedCodecs.isNullOrBlank()) {
            append("&supported_codecs=").append(encode(supportedCodecs))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
