package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.network.PlaybackTokenSource
import dev.teyd.justintv.core.network.PlayerTypes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Picks a playable, ad-free stream URL.
 *
 * 1. m3u8 proxies are probed **in parallel**. The first one to return a clean playlist wins
 *    and the rest are cancelled, so a dead or hanging proxy never delays a healthy one.
 * 2. direct usher URLs built from a different `playerType`
 * 3. the plain direct stream, as a last resort
 *
 * Steps 2 and 3 are prepared while step 1 runs, once it has taken longer than
 * [hedgeDelayMs] or has finished without a winner. Each of them costs a token request and
 * one or two playlist fetches, so running them one after another behind a failed proxy race
 * was where most of the wait went. When a proxy wins quickly they never start.
 *
 * The resolver never throws for an individual candidate failure; it moves on. It only fails
 * when there is no stream at all (offline channel, or the network is down).
 */
class PlaylistResolver(
    private val api: PlaybackTokenSource,
    private val proxies: List<ProxyEndpoint> = DefaultProxies.ALL,
    private val verifier: PlaylistVerifier,
    private val maxProxiesPerAttempt: Int = DEFAULT_MAX_PROXIES,
    private val hedgeDelayMs: Long = DEFAULT_HEDGE_DELAY_MS,
    private val raceDeadlineMs: Long = DEFAULT_RACE_DEADLINE_MS,
) {
    private sealed interface ProbeResult {
        data class Clean(
            val proxy: ProxyEndpoint,
        ) : ProbeResult

        data class Ads(
            val proxy: ProxyEndpoint,
        ) : ProbeResult

        data class Failed(
            val proxy: ProxyEndpoint,
            val error: PlaybackException?,
        ) : ProbeResult
    }

    private class SwapResult(
        val url: String?,
        val adFree: Boolean,
        val failure: PlaybackException?,
    )

    private class DirectResult(
        val url: String?,
        val failure: Throwable?,
    )

    /**
     * Resolves [login] to a stream.
     *
     * @param excluding proxy hosts that should not be tried again. The UI adds a proxy here
     * after an ad break is detected on it, so the retry lands somewhere else.
     * @param adBlockEnabled when false, skips proxy probing and player-type swaps entirely and
     * plays the plain direct stream.
     * @param disabledProxies proxy hosts the viewer switched off in settings.
     * @param onStatus progress messages for the UI, in user-facing language.
     */
    suspend fun resolve(
        login: String,
        excluding: Set<String> = emptySet(),
        adBlockEnabled: Boolean = true,
        disabledProxies: Set<String> = emptySet(),
        maxAttempts: Int = -1,
        onStatus: (String) -> Unit = {},
    ): ResolvedPlayback =
        coroutineScope {
            if (!adBlockEnabled) {
                onStatus("Starting the stream")
                val directUrl = api.directStreamUrl(login, PlayerTypes.SITE)
                return@coroutineScope ResolvedPlayback(directUrl, PlaybackMethod.Direct, verified = false)
            }

            val candidates =
                proxies
                    .filterNot { it.host in excluding || it.host in disabledProxies }
                    .take(if (maxAttempts > 0) maxAttempts else maxProxiesPerAttempt)

            val raceFinished = CompletableDeferred<Unit>()

            suspend fun awaitHedge() {
                withTimeoutOrNull(hedgeDelayMs) { raceFinished.await() }
            }

            val swaps: List<Deferred<SwapResult>> =
                PlayerTypes.SWAP_ORDER.map { playerType ->
                    async {
                        awaitHedge()
                        probeSwap(login, playerType)
                    }
                }
            val direct: Deferred<DirectResult> =
                async {
                    awaitHedge()
                    try {
                        DirectResult(api.directStreamUrl(login, PlayerTypes.SITE), null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DirectResult(null, e)
                    }
                }

            var lastFailure: PlaybackException? = null

            if (candidates.isNotEmpty()) {
                onStatus("Finding the fastest source")
                // A proxy that has not answered by the deadline is not worth waiting for while a
                // prepared fallback is sitting there.
                val outcome =
                    withTimeoutOrNull(raceDeadlineMs) { raceProxies(login, candidates) }
                        ?: RaceOutcome(winner = null, lastFailure = null)
                raceFinished.complete(Unit)
                outcome.winner?.let { proxy ->
                    swaps.forEach { it.cancel() }
                    direct.cancel()
                    onStatus("Playing via ${proxy.host}")
                    return@coroutineScope ResolvedPlayback(
                        proxy.liveUrl(login),
                        PlaybackMethod.Proxied(proxy.host),
                        verified = true,
                    )
                }
                lastFailure = outcome.lastFailure
            } else {
                raceFinished.complete(Unit)
            }

            onStatus("Trying another way in")
            for ((index, playerType) in PlayerTypes.SWAP_ORDER.withIndex()) {
                val swap = swaps[index].await()
                if (swap.adFree && swap.url != null) {
                    swaps.forEach { it.cancel() }
                    direct.cancel()
                    onStatus("Playing with the $playerType player")
                    return@coroutineScope ResolvedPlayback(
                        swap.url,
                        PlaybackMethod.PlayerTypeSwap(playerType),
                        verified = true,
                    )
                }
                swap.failure?.let { lastFailure = it }
            }

            onStatus("No ad-free stream found, playing directly")
            val result = direct.await()
            val url = result.url
            if (url == null) {
                val failure = result.failure
                throw if (failure is PlaybackException) lastFailure ?: failure else failure ?: IllegalStateException()
            }
            ResolvedPlayback(url, PlaybackMethod.Direct, verified = false)
        }

    private suspend fun probeSwap(
        login: String,
        playerType: String,
    ): SwapResult {
        val url =
            try {
                api.directStreamUrl(login, playerType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlaybackException) {
                return SwapResult(null, adFree = false, failure = e)
            } catch (_: Exception) {
                return SwapResult(null, adFree = false, failure = null)
            }
        return try {
            SwapResult(url, adFree = verifier.isAdFree(url), failure = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaybackException) {
            SwapResult(url, adFree = false, failure = e)
        } catch (_: Exception) {
            SwapResult(url, adFree = false, failure = null)
        }
    }

    private class RaceOutcome(
        val winner: ProxyEndpoint?,
        val lastFailure: PlaybackException?,
    )

    /** Probes every candidate at once and returns as soon as one is clean. */
    private suspend fun raceProxies(
        login: String,
        candidates: List<ProxyEndpoint>,
    ): RaceOutcome =
        coroutineScope {
            val results = Channel<ProbeResult>(Channel.UNLIMITED)
            val jobs =
                candidates.map { proxy ->
                    launch { results.send(probe(proxy, login)) }
                }

            var winner: ProxyEndpoint? = null
            var lastFailure: PlaybackException? = null
            for (ignored in candidates.indices) {
                when (val result = results.receive()) {
                    is ProbeResult.Clean -> {
                        winner = result.proxy
                        break
                    }

                    is ProbeResult.Ads -> {
                        Unit
                    }

                    is ProbeResult.Failed -> {
                        lastFailure = result.error ?: lastFailure
                    }
                }
            }
            jobs.forEach { it.cancel() }
            results.close()
            RaceOutcome(winner, lastFailure)
        }

    private suspend fun probe(
        proxy: ProxyEndpoint,
        login: String,
    ): ProbeResult =
        try {
            if (verifier.isAdFree(proxy.liveUrl(login))) ProbeResult.Clean(proxy) else ProbeResult.Ads(proxy)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaybackException) {
            ProbeResult.Failed(proxy, e)
        } catch (_: Exception) {
            ProbeResult.Failed(proxy, null)
        }

    companion object {
        const val DEFAULT_MAX_PROXIES = 6

        /** Healthy proxies answer well inside this. Past it, the fallbacks start preparing. */
        const val DEFAULT_HEDGE_DELAY_MS = 1_200L

        /** The longest the proxy race may hold up the fallbacks. */
        const val DEFAULT_RACE_DEADLINE_MS = 4_000L
    }
}
