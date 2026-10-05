package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.network.PlaybackTokenSource
import dev.teyd.justintv.core.network.PlayerTypes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Picks a playable, ad-free stream URL.
 *
 * 1. m3u8 proxies are probed **in parallel**. The first one to return a clean playlist wins
 *    and the rest are cancelled, so a dead or hanging proxy never delays a healthy one.
 * 2. direct usher URLs built from a different `playerType`
 * 3. the plain direct stream, as a last resort
 *
 * The resolver never throws for an individual candidate failure; it moves on. It only fails
 * when there is no stream at all (offline channel, or the network is down).
 */
class PlaylistResolver(
    private val api: PlaybackTokenSource,
    private val proxies: List<ProxyEndpoint> = DefaultProxies.ALL,
    private val verifier: PlaylistVerifier,
    private val maxProxiesPerAttempt: Int = DEFAULT_MAX_PROXIES,
) {
    private sealed interface ProbeResult {
        data class Clean(val proxy: ProxyEndpoint) : ProbeResult
        data class Ads(val proxy: ProxyEndpoint) : ProbeResult
        data class Failed(val proxy: ProxyEndpoint, val error: PlaybackException?) : ProbeResult
    }

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
    ): ResolvedPlayback {
        if (!adBlockEnabled) {
            onStatus("Ad blocking is off, playing directly")
            val directUrl = api.directStreamUrl(login, PlayerTypes.SITE)
            return ResolvedPlayback(directUrl, PlaybackMethod.Direct, verified = false)
        }

        val candidates = proxies
            .filterNot { it.host in excluding || it.host in disabledProxies }
            .take(if (maxAttempts > 0) maxAttempts else maxProxiesPerAttempt)

        var lastFailure: PlaybackException? = null

        if (candidates.isNotEmpty()) {
            onStatus("Checking ${candidates.size} ad-free sources…")
            val outcome = raceProxies(login, candidates, onStatus)
            outcome.winner?.let { proxy ->
                onStatus("Playing via ${proxy.host}")
                return ResolvedPlayback(proxy.liveUrl(login), PlaybackMethod.Proxied(proxy.host), verified = true)
            }
            lastFailure = outcome.lastFailure
        }

        for (playerType in PlayerTypes.SWAP_ORDER) {
            onStatus("Trying the $playerType player…")
            val url = try {
                api.directStreamUrl(login, playerType)
            } catch (e: PlaybackException) {
                lastFailure = e
                continue
            }
            try {
                if (verifier.isAdFree(url)) {
                    onStatus("Playing with the $playerType player")
                    return ResolvedPlayback(url, PlaybackMethod.PlayerTypeSwap(playerType), verified = true)
                }
            } catch (e: PlaybackException) {
                lastFailure = e
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to the next player type.
            }
        }

        onStatus("No ad-free stream found, playing directly")
        val directUrl = try {
            api.directStreamUrl(login, PlayerTypes.SITE)
        } catch (e: PlaybackException) {
            throw lastFailure ?: e
        }
        return ResolvedPlayback(directUrl, PlaybackMethod.Direct, verified = false)
    }

    private class RaceOutcome(val winner: ProxyEndpoint?, val lastFailure: PlaybackException?)

    /** Probes every candidate at once and returns as soon as one is clean. */
    private suspend fun raceProxies(
        login: String,
        candidates: List<ProxyEndpoint>,
        onStatus: (String) -> Unit,
    ): RaceOutcome = coroutineScope {
        val results = Channel<ProbeResult>(Channel.UNLIMITED)
        val jobs = candidates.map { proxy ->
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

                is ProbeResult.Ads -> onStatus("${result.proxy.host} still serves ads")
                is ProbeResult.Failed -> {
                    lastFailure = result.error ?: lastFailure
                    onStatus("${result.proxy.host} is unavailable")
                }
            }
        }
        jobs.forEach { it.cancel() }
        results.close()
        RaceOutcome(winner, lastFailure)
    }

    private suspend fun probe(proxy: ProxyEndpoint, login: String): ProbeResult = try {
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
    }
}
