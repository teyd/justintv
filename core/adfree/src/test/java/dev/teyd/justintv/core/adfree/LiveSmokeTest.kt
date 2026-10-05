package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.network.GqlClient
import dev.teyd.justintv.core.network.OkHttpPlaylistFetcher
import dev.teyd.justintv.core.network.TwitchDirectoryApi
import dev.teyd.justintv.core.network.TwitchHttpClient
import dev.teyd.justintv.core.network.TwitchPlaybackApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Talks to the real Twitch and the real proxies. Skipped unless `JUSTINTV_LIVE=1`, because it
 * depends on the network and on which channels are live. Run it with:
 *
 *     JUSTINTV_LIVE=1 ./gradlew :core:adfree:testDebugUnitTest --tests '*LiveSmokeTest*'
 *
 * It prints what it finds; read the output in the test report.
 */
class LiveSmokeTest {

    private val base = TwitchHttpClient.create()
    private val probe = base.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
    private val ping = base.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private fun requireLive() = assumeTrue(System.getenv("JUSTINTV_LIVE") == "1")

    @Test
    fun `browse data loads and respects the language filter`() = runBlocking {
        requireLive()
        val directory = TwitchDirectoryApi(GqlClient(base))

        val all = directory.topStreams(emptySet())
        val german = directory.topStreams(setOf("DE"))
        val games = directory.topGames()
        val firstGame = games.first().name
        val inGame = directory.gameStreams(firstGame, setOf("EN"))

        println("LIVE streams(all)=${all.size} first=${all.firstOrNull()?.login}")
        println("LIVE streams(DE)=${german.size} languages=${german.map { it.language }.distinct()}")
        println("LIVE games=${games.size} first=$firstGame (${games.first().viewerCount} viewers)")
        println("LIVE game '$firstGame' EN streams=${inGame.size} languages=${inGame.map { it.language }.distinct()}")
        check(all.isNotEmpty()) { "no live streams" }
        check(games.size > 50) { "expected many games, got ${games.size}" }
        check(german.all { it.language == "DE" }) { "German filter leaked other languages" }
    }

    @Test
    fun `proxy health checks finish quickly even when hosts hang`() = runBlocking {
        requireLive()
        val checker = ProxyHealthChecker(OkHttpPlaylistFetcher(ping))
        val started = System.nanoTime()

        val results = coroutineScope {
            DefaultProxies.ALL.map { proxy ->
                async { proxy.host to checker.isOnline(proxy) }
            }.awaitAll()
        }

        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        results.forEach { (host, online) -> println("LIVE ping ${if (online) "ONLINE " else "offline"} $host") }
        println("LIVE all ${results.size} pings finished in %.1fs".format(seconds))
        check(seconds < 6.0) { "pings took %.1fs".format(seconds) }
    }

    @Test
    fun `resolver picks a working source for a live channel in seconds`() = runBlocking {
        requireLive()
        val directory = TwitchDirectoryApi(GqlClient(base))
        val channel = directory.topStreams(emptySet()).first().login
        val api = TwitchPlaybackApi(base)
        val resolver = PlaylistResolver(api, DefaultProxies.ALL, PlaylistVerifier(OkHttpPlaylistFetcher(probe)))
        val started = System.nanoTime()

        val result = resolver.resolve(channel) { println("LIVE status: $it") }

        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        println("LIVE resolved '$channel' via ${result.method.label} verified=${result.verified} in %.1fs".format(seconds))
        val master = api.fetchPlaylist(result.playlistUrl)
        println("LIVE master playlist: ${master.lineSequence().count()} lines, variants=${Regex("#EXT-X-STREAM-INF").findAll(master).count()}")
        check(master.startsWith("#EXTM3U")) { "not an HLS playlist" }
        check(seconds < 15.0) { "resolution took %.1fs".format(seconds) }
    }
}
