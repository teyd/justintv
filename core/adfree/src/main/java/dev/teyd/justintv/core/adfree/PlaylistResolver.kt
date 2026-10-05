package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.network.PlaybackException
import dev.teyd.justintv.core.network.PlaybackTokenSource
import dev.teyd.justintv.core.network.PlayerTypes
import dev.teyd.justintv.core.network.UsherUrlBuilder

/**
 * Picks a playable, ad-free stream URL.
 *
 * Candidates are tried in order and each one is verified before it is returned:
 *
 * 1. m3u8 proxies, in the order they are configured
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
    /**
     * Resolves [login] to a stream.
     *
     * @param excluding proxy hosts that should not be tried again. The UI adds a proxy here
     * after an ad break is detected on it, so the retry lands somewhere else.
     * @param onStatus progress messages for the UI, in user-facing language.
     */
    suspend fun resolve(
        login: String,
        excluding: Set<String> = emptySet(),
        maxAttempts: Int = -1,
        onStatus: (String) -> Unit = {},
    ): ResolvedPlayback {
        val candidates = proxies
            .filterNot { it.host in excluding }
            .let { if (maxAttempts > 0) it.take(maxAttempts) else it.take(maxProxiesPerAttempt) }

        var lastFailure: PlaybackException? = null

        for (proxy in candidates) {
            onStatus("Trying ${proxy.host}…")
            val url = proxy.liveUrl(login)
            try {
                if (verifier.isAdFree(url)) {
                    onStatus("Playing via ${proxy.host}")
                    return ResolvedPlayback(url, PlaybackMethod.Proxied(proxy.host), verified = true)
                }
                onStatus("${proxy.host} still serves ads, trying the next option…")
            } catch (e: PlaybackException) {
                lastFailure = e
                onStatus("${proxy.host} is unavailable, trying the next option…")
            } catch (e: Exception) {
                onStatus("${proxy.host} failed, trying the next option…")
            }
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

    companion object {
        const val DEFAULT_MAX_PROXIES = 4
    }
}
