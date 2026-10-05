package dev.teyd.justintv.core.adfree

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.network.TextFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaylistVerifierTest {

    private class FakeFetcher(private val bodies: Map<String, String>) : TextFetcher {
        val requested = mutableListOf<String>()

        override suspend fun fetchText(url: String): String {
            requested += url
            return bodies[url] ?: throw IllegalStateException("unexpected url $url")
        }
    }

    private val masterWithVariant = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=630000
        https://video-edge.example/360p.m3u8
    """.trimIndent()

    private val mediaClean = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXTINF:2.000,live
        https://video-edge.example/segment.ts
    """.trimIndent()

    private val mediaWithAd = mediaClean + "\n" +
        """#EXT-X-DATERANGE:ID="stitched-ad-1",CLASS="twitch-stitched-ad",START-DATE="2026-10-05T00:31:40.000Z",DURATION=30.0"""

    @Test
    fun `follows a master playlist down to its first variant`() = runTest {
        val masterUrl = "https://proxy.example/live/channel.m3u8"
        val fetcher = FakeFetcher(
            mapOf(
                masterUrl to masterWithVariant,
                "https://video-edge.example/360p.m3u8" to mediaClean,
            ),
        )

        val adFree = PlaylistVerifier(fetcher).isAdFree(masterUrl)

        assertThat(adFree).isTrue()
        assertThat(fetcher.requested).hasSize(2)
    }

    @Test
    fun `rejects a variant that carries ad markers`() = runTest {
        val masterUrl = "https://proxy.example/live/channel.m3u8"
        val fetcher = FakeFetcher(
            mapOf(
                masterUrl to masterWithVariant,
                "https://video-edge.example/360p.m3u8" to mediaWithAd,
            ),
        )

        assertThat(PlaylistVerifier(fetcher).isAdFree(masterUrl)).isFalse()
    }

    @Test
    fun `checks a media playlist directly`() = runTest {
        val url = "https://proxy.example/live/channel.m3u8"
        val fetcher = FakeFetcher(mapOf(url to mediaWithAd))

        assertThat(PlaylistVerifier(fetcher).isAdFree(url)).isFalse()
        assertThat(fetcher.requested).hasSize(1)
    }

    @Test
    fun `accepts a playlist that cannot be inspected further`() = runTest {
        // No variants and no segments: nothing to inspect, so do not reject a playable URL.
        val url = "https://proxy.example/live/channel.m3u8"
        val fetcher = FakeFetcher(mapOf(url to "#EXTM3U\n#EXT-X-VERSION:3"))

        assertThat(PlaylistVerifier(fetcher).isAdFree(url)).isTrue()
    }
}
