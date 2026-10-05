package dev.teyd.justintv.core.chat

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds the emote index for a channel from every enabled provider.
 *
 * A provider that is down or has nothing for the channel contributes nothing; it never stops
 * chat from working. Global sets are cached per process, keyed by which providers are enabled,
 * so toggling 7TV/BTTV/FFZ in settings takes effect on the next chat without a stale cache.
 *
 * Priority, lowest first: BTTV, FFZ, 7TV, with channel sets above global ones. If two emotes
 * share a name, the higher one wins.
 */
class EmoteRepository(
    private val providers: List<EmoteProvider>,
    /** Re-read on every index build so settings changes apply immediately. */
    private val enabledSources: Flow<Set<EmoteSource>> = flowOf(EmoteSource.entries.toSet()),
) {
    private val globalLock = Mutex()
    private var globalCache: Pair<Set<EmoteSource>, List<Emote>>? = null

    suspend fun indexFor(roomId: String?): EmoteIndex = coroutineScope {
        val enabled = enabledProviders()
        val globalJob = async { global(enabled) }
        val channelJob = async { if (roomId == null) emptyList() else channel(enabled, roomId) }
        val (global, channel) = awaitAll(globalJob, channelJob)
        EmoteIndex.Builder().addAll(global).addAll(channel).build()
    }

    private suspend fun enabledProviders(): List<EmoteProvider> {
        val allowed = enabledSources.first()
        return providers.filter { it.source in allowed }
    }

    private suspend fun global(providers: List<EmoteProvider>): List<Emote> = globalLock.withLock {
        val sources = providers.mapTo(mutableSetOf()) { it.source }
        val cached = globalCache
        if (cached != null && cached.first == sources) {
            cached.second
        } else {
            collect(providers) { it.global() }.also { if (it.isNotEmpty()) globalCache = sources to it }
        }
    }

    private suspend fun channel(providers: List<EmoteProvider>, roomId: String): List<Emote> =
        collect(providers) { it.channel(roomId) }

    /** Runs [load] on every provider concurrently and orders the results by priority. */
    private suspend fun collect(
        providers: List<EmoteProvider>,
        load: suspend (EmoteProvider) -> List<Emote>,
    ): List<Emote> = coroutineScope {
        providers
            .map { provider ->
                async {
                    try {
                        load(provider)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatten()
            .sortedBy { it.source.ordinal }
    }
}
