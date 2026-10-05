package dev.teyd.justintv.core.adfree

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.network.PlaybackTokenSource
import dev.teyd.justintv.core.network.TextFetcher
import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Proxies are probed in parallel: a slow or hanging proxy must never delay a healthy one. */
class PlaylistResolverRaceTest {
    private val cleanMedia =
        """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXTINF:2.000,live
        https://video-edge.example/segment.ts
        """.trimIndent()

    private val tokenSource =
        object : PlaybackTokenSource {
            override suspend fun playbackAccessToken(
                login: String,
                playerType: String,
            ) = PlaybackAccessToken("v", "s")

            override suspend fun directStreamUrl(
                login: String,
                playerType: String,
            ) = "https://usher.ttvnw.net/api/v2/channel/hls/$login.m3u8?playerType=$playerType"
        }

    /** Each host answers after its own delay; [hangMs] simulates a proxy that never answers. */
    private class DelayedFetcher(
        private val delays: Map<String, Long>,
        private val body: String,
        private val failing: Set<String> = emptySet(),
    ) : TextFetcher {
        override suspend fun fetchText(url: String): String {
            val host = url.substringAfter("://").substringBefore('/')
            delay(delays[host] ?: 0)
            if (host in failing) throw PlaybackException.Network("timed out")
            return body
        }
    }

    @Test
    fun `a hanging proxy does not delay a healthy one`() =
        runTest {
            val proxies =
                listOf(
                    ProxyEndpoint("hangs.example"),
                    ProxyEndpoint("fast.example"),
                )
            val fetcher =
                DelayedFetcher(
                    delays = mapOf("hangs.example" to 60_000L, "fast.example" to 200L),
                    body = cleanMedia,
                )
            val resolver = PlaylistResolver(tokenSource, proxies, PlaylistVerifier(fetcher))

            val result = resolver.resolve("dona")

            assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("fast.example"))
            // Sequential probing would have waited the full minute for the first proxy.
            assertThat(currentTime).isLessThan(5_000L)
        }

    @Test
    fun `the fastest clean proxy wins even when it is listed last`() =
        runTest {
            val proxies =
                listOf(
                    ProxyEndpoint("slow.example"),
                    ProxyEndpoint("medium.example"),
                    ProxyEndpoint("quick.example"),
                )
            val fetcher =
                DelayedFetcher(
                    delays = mapOf("slow.example" to 3_000L, "medium.example" to 1_000L, "quick.example" to 100L),
                    body = cleanMedia,
                )
            val resolver = PlaylistResolver(tokenSource, proxies, PlaylistVerifier(fetcher))

            val result = resolver.resolve("dona")

            assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("quick.example"))
            assertThat(currentTime).isLessThan(1_000L)
        }

    @Test
    fun `fast failures do not stop a slower healthy proxy from winning`() =
        runTest {
            val proxies =
                listOf(
                    ProxyEndpoint("broken.example"),
                    ProxyEndpoint("slowbutgood.example"),
                )
            val fetcher =
                DelayedFetcher(
                    delays = mapOf("broken.example" to 50L, "slowbutgood.example" to 2_000L),
                    body = cleanMedia,
                    failing = setOf("broken.example"),
                )
            val resolver = PlaylistResolver(tokenSource, proxies, PlaylistVerifier(fetcher))

            val result = resolver.resolve("dona")

            assertThat(result.method).isEqualTo(PlaybackMethod.Proxied("slowbutgood.example"))
        }

    @Test
    fun `when every proxy hangs the resolver still falls back to a direct stream`() =
        runTest {
            val proxies = listOf(ProxyEndpoint("a.example"), ProxyEndpoint("b.example"))
            // Proxies fail after a bounded delay (as a real probe timeout would), then direct is used.
            val fetcher =
                DelayedFetcher(
                    delays = mapOf("a.example" to 6_000L, "b.example" to 6_000L, "usher.ttvnw.net" to 100L),
                    body = cleanMedia,
                    failing = setOf("a.example", "b.example"),
                )
            val resolver = PlaylistResolver(tokenSource, proxies, PlaylistVerifier(fetcher))

            val result = resolver.resolve("dona")

            // Player-type swap is clean here, so that is what is chosen before plain direct.
            assertThat(result.method).isInstanceOf(PlaybackMethod.PlayerTypeSwap::class.java)
            assertThat(result.verified).isTrue()
        }

    @Test
    fun `hanging proxies do not hold up the fallback for their whole timeout`() =
        runTest {
            val proxies = listOf(ProxyEndpoint("a.example"), ProxyEndpoint("b.example"))
            val fetcher =
                DelayedFetcher(
                    delays = mapOf("a.example" to 60_000L, "b.example" to 60_000L, "usher.ttvnw.net" to 300L),
                    body = cleanMedia,
                )
            val resolver = PlaylistResolver(tokenSource, proxies, PlaylistVerifier(fetcher))

            val result = resolver.resolve("dona")

            assertThat(result.method).isInstanceOf(PlaybackMethod.PlayerTypeSwap::class.java)
            // The race is capped at four seconds and the fallback was already prepared.
            assertThat(currentTime).isLessThan(5_000L)
        }

    @Test
    fun `fallbacks are not started when a proxy wins quickly`() =
        runTest {
            val seen = mutableListOf<String>()
            val trackingTokens =
                object : PlaybackTokenSource {
                    override suspend fun playbackAccessToken(
                        login: String,
                        playerType: String,
                    ) = PlaybackAccessToken("v", "s")

                    override suspend fun directStreamUrl(
                        login: String,
                        playerType: String,
                    ): String {
                        seen += playerType
                        return "https://usher.ttvnw.net/api/v2/channel/hls/$login.m3u8?playerType=$playerType"
                    }
                }
            val fetcher = DelayedFetcher(delays = mapOf("fast.example" to 100L), body = cleanMedia)
            val resolver = PlaylistResolver(trackingTokens, listOf(ProxyEndpoint("fast.example")), PlaylistVerifier(fetcher))

            resolver.resolve("dona")

            assertThat(seen).isEmpty()
        }

    @Test
    fun `candidate count is limited`() =
        runTest {
            val proxies = (1..10).map { ProxyEndpoint("p$it.example") }
            val requested = mutableSetOf<String>()
            val fetcher =
                TextFetcher { url ->
                    requested += url.substringAfter("://").substringBefore('/')
                    throw PlaybackException.Network("down")
                }
            val resolver =
                PlaylistResolver(
                    api = tokenSource,
                    proxies = proxies,
                    verifier = PlaylistVerifier(fetcher),
                    maxProxiesPerAttempt = 3,
                )

            resolver.resolve("dona")

            assertThat(requested.filter { it.startsWith("p") && it.endsWith(".example") }).hasSize(3)
        }
}
