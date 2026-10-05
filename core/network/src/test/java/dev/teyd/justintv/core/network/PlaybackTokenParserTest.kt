package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackTokenParserTest {

    /** Trimmed from a real response for an anonymous `playbackAccessToken` request. */
    private val successBody = """
        {"data":{"streamPlaybackAccessToken":{"value":"{\"channel\":\"dona\",\"expires\":1791161251}","signature":"9f8a7b6c5d","__typename":"PlaybackAccessToken"}}}
    """.trimIndent()

    @Test
    fun `parses value and signature`() {
        val result = PlaybackTokenParser.parse(successBody)

        assertThat(result).isInstanceOf(PlaybackTokenParser.ParseResult.Success::class.java)
        val token = (result as PlaybackTokenParser.ParseResult.Success).token
        assertThat(token.value).contains("\"channel\":\"dona\"")
        assertThat(token.signature).isEqualTo("9f8a7b6c5d")
    }

    @Test
    fun `reports an offline channel when the token is null`() {
        val body = """{"data":{"streamPlaybackAccessToken":null}}"""

        val result = PlaybackTokenParser.parse(body)

        assertThat(result).isInstanceOf(PlaybackTokenParser.ParseResult.Failure::class.java)
        assertThat((result as PlaybackTokenParser.ParseResult.Failure).reason)
            .isEqualTo(PlaybackTokenParser.FailureReason.OfflineOrUnknownChannel)
    }

    @Test
    fun `reports graphql errors`() {
        val body = """{"errors":[{"message":"PersistedQueryNotFound"}]}"""

        val result = PlaybackTokenParser.parse(body)

        assertThat(result).isInstanceOf(PlaybackTokenParser.ParseResult.Failure::class.java)
        val failure = result as PlaybackTokenParser.ParseResult.Failure
        assertThat(failure.reason).isEqualTo(PlaybackTokenParser.FailureReason.GraphQlError)
        assertThat(failure.message).contains("PersistedQueryNotFound")
    }

    @Test
    fun `reports malformed bodies`() {
        val result = PlaybackTokenParser.parse("<html>gateway timeout</html>")

        assertThat(result).isInstanceOf(PlaybackTokenParser.ParseResult.Failure::class.java)
        assertThat((result as PlaybackTokenParser.ParseResult.Failure).reason)
            .isEqualTo(PlaybackTokenParser.FailureReason.Malformed)
    }
}
