package dev.teyd.justintv.core.adfree

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.network.PlaybackTokenSource
import dev.teyd.justintv.core.network.TextFetcher
import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaylistResolverTest {

    private val proxies = listOf(
        ProxyEndpoint("proxy-one.example"),
        ProxyEndpoint("proxy-two.example"),
    )

    private val token = PlaybackAccessToken(value = "token-value", signature = "sig-value")

    private class FakeTokenSource(
        private val failing: Boolean = false,
    ) : PlaybackTokenSource {
        override suspend fun playbackAccessToken(login: String, playerType: String): PlaybackAccessToken {
            if (failing) throw PlaybackException.ChannelUnavailable("Channel is offline or does not exist")
            return PlaybackAccessToken(value = "value-$playerType", signature = "sig-$playerType")
        }

        override suspend fun directStreamUrl(login: String, playerType: String): String {
            playbackAccessToken(login, playerType)
            return "https://usher.ttvnw.net/api/v2/channel/hls/$login.m3u8?playerType=$playerType"
        }
    }

    /** Serves media playlists per host, throwing for hosts in [offlineHosts]. */
    private class FakeFetcher(
        private val adsByHost: Map<String, Boolean>,
        private val offlineHosts: Set<String> = emptySet(),
    ) : TextFetcher {
        val requested = mutableListOf<String>()

        override suspend fun fetchText(url: String): String {
            requested += url
            val host = url.substringAfter("://").substringBefore('/')
            if (host in offlineHosts) throw PlaybackException.Network("connect timed out")
            val hasAds = adsByHost[host] ?: false
            return if (hasAds) mediaPlaylistWithAd() else cleanMediaPlaylist()
        }

        private fun cleanMediaPlaylist() = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:2.000,live
            https://video-edge.example/segment.ts
        """.trimIndent()

        private fun mediaPlaylistWithAd() = cleanMediaPlaylist() + "\n" +
            """#EXT-X-DATERANGE:ID="stitched-ad-1",CLASS="twitch-stitched-ad",START-DATE="2026-10-05T00:31:40.000Z",DURATION=30.0"""
    }

    private fun resolver(
        adsByHost: Map<String, Boolean>,
        offlineHosts: Set<String> = emptySet(),
        api: PlaybackTokenSource = FakeTokenSource(),
    ): Pair<PlaylistResolver, FakeFetcher> {
        val fetcher = FakeFetcher(adsByHost, offlineHosts)
        return PlaylistResolver(
            api = api,
            proxies = proxies,
            verifier = PlaylistVerifier(fetcher),
        ) to fetcher
    }

    @Test
    fun `uses the first proxy when it is clean`() = runTest {
        val (resolver, _) = resolver(adsByHost = emptyMap())

        val result = resolver.resolve("dona")

        assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("proxy-one.example"))
        assertThat(result.verified).isTrue()
        assertThat(result.playlistUrl).startsWith("https://proxy-one.example/live/dona")
    }

    @Test
    fun `moves to the next proxy when the first one is offline`() = runTest {
        val (resolver, _) = resolver(
            adsByHost = emptyMap(),
            offlineHosts = setOf("proxy-one.example"),
        )

        val result = resolver.resolve("dona")

        assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("proxy-two.example"))
    }

    @Test
    fun `moves to the next proxy when the first one still serves ads`() = runTest {
        val (resolver, _) = resolver(
            adsByHost = mapOf("proxy-one.example" to true),
        )

        val result = resolver.resolve("dona")

        assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("proxy-two.example"))
    }

    @Test
    fun `falls back to a player type swap when every proxy serves ads`() = runTest {
        val (resolver, _) = resolver(
            adsByHost = mapOf(
                "proxy-one.example" to true,
                "proxy-two.example" to true,
            ),
        )

        val result = resolver.resolve("dona")

        assertThat(result.method).isEqualTo(PlaybackMethod.PlayerTypeSwap("popout"))
        assertThat(result.verified).isTrue()
        assertThat(result.playlistUrl).contains("playerType=popout")
    }

    @Test
    fun `falls back to the direct stream when nothing verifies`() = runTest {
        val (resolver, _) = resolver(
            adsByHost = mapOf(
                "proxy-one.example" to true,
                "proxy-two.example" to true,
                "usher.ttvnw.net" to true,
            ),
        )

        val result = resolver.resolve("dona")

        assertThat(result.method).isEqualTo(PlaybackMethod.Direct)
        assertThat(result.verified).isFalse()
        assertThat(result.playlistUrl).contains("playerType=site")
    }

    @Test
    fun `skips proxies the caller excluded`() = runTest {
        val (resolver, _) = resolver(adsByHost = emptyMap())

        val result = resolver.resolve("dona", excluding = setOf("proxy-one.example"))

        assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("proxy-two.example"))
    }

    @Test
    fun `ad blocking off skips proxies and players and uses the site stream`() = runTest {
        val (resolver, fetcher) = resolver(adsByHost = emptyMap())

        val result = resolver.resolve("dona", adBlockEnabled = false)

        assertThat(result.method).isEqualTo(PlaybackMethod.Direct)
        assertThat(result.verified).isFalse()
        assertThat(result.playlistUrl).contains("playerType=site")
        assertThat(fetcher.requested).isEmpty()
    }

    @Test
    fun `proxies switched off in settings are never probed`() = runTest {
        val (resolver, fetcher) = resolver(adsByHost = emptyMap())

        val result = resolver.resolve("dona", disabledProxies = setOf("proxy-one.example"))

        assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("proxy-two.example"))
        assertThat(fetcher.requested.none { "proxy-one.example" in it }).isTrue()
    }

    @Test
    fun `all proxies switched off falls through to a player type swap`() = runTest {
        val (resolver, _) = resolver(adsByHost = emptyMap())

        val result = resolver.resolve(
            login = "dona",
            disabledProxies = setOf("proxy-one.example", "proxy-two.example"),
        )

        assertThat(result.method).isEqualTo(PlaybackMethod.PlayerTypeSwap("popout"))
    }

    @Test
    fun `fails when the channel itself is unavailable`() = runTest {
        val (resolver, _) = resolver(
            adsByHost = mapOf(
                "proxy-one.example" to true,
                "proxy-two.example" to true,
                "usher.ttvnw.net" to true,
            ),
            api = FakeTokenSource(failing = true),
        )

        val failure = runCatching { resolver.resolve("dona") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(PlaybackException.ChannelUnavailable::class.java)
    }

    @Test
    fun `reports progress through the status callback`() = runTest {
        val (resolver, _) = resolver(adsByHost = mapOf("proxy-one.example" to true))
        val updates = mutableListOf<String>()

        resolver.resolve("dona") { updates += it }

        assertThat(updates).isNotEmpty()
        assertThat(updates.last()).contains("Playing via proxy-two.example")
    }
}
