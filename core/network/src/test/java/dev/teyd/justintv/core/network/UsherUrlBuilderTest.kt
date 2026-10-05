package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import org.junit.Test

class UsherUrlBuilderTest {

    private val token = PlaybackAccessToken(
        value = """{"channel":"dona","expires":1791161251,"platform":"web"}""",
        signature = "abc123sig",
    )

    @Test
    fun `builds a v2 usher url with the expected parameters`() {
        val url = UsherUrlBuilder.streamUrl(
            login = "dona",
            token = token,
            randomToken = 42,
        )

        assertThat(url).startsWith("https://usher.ttvnw.net/api/v2/channel/hls/dona.m3u8?")
        assertThat(url).contains("allow_source=true")
        assertThat(url).contains("allow_audio_only=true")
        assertThat(url).contains("fast_bread=true")
        assertThat(url).contains("include_unavailable=true")
        assertThat(url).contains("platform=web")
        assertThat(url).contains("p=42")
    }

    @Test
    fun `url encodes the token and signature`() {
        val url = UsherUrlBuilder.streamUrl("dona", token, randomToken = 1)

        assertThat(url).contains("sig=abc123sig")
        // Quotes, braces and colons must be percent-encoded, not raw.
        assertThat(url).doesNotContain("\"")
        assertThat(url).contains("%7B")
        assertThat(url).contains("%22")
    }

    @Test
    fun `lowercases the channel login`() {
        val url = UsherUrlBuilder.streamUrl("SomeChannel", token, randomToken = 1)

        assertThat(url).contains("/somechannel.m3u8")
    }

    @Test
    fun `includes supported codecs only when provided`() {
        val without = UsherUrlBuilder.streamUrl("dona", token, randomToken = 1)
        val with = UsherUrlBuilder.streamUrl(
            "dona",
            token,
            supportedCodecs = "avc1,mp4a",
            randomToken = 1,
        )

        assertThat(without).doesNotContain("supported_codecs")
        assertThat(with).contains("supported_codecs=avc1%2Cmp4a")
    }
}
