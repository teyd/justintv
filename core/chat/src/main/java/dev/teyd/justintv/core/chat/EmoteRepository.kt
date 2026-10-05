package dev.teyd.justintv.core.chat

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds the emote index for a channel from every provider.
 *
 * A provider that is down or has nothing for the channel contributes nothing; it never stops
 * chat from working. Global sets are loaded once per process.
 *
 * Priority, lowest first: BTTV, FFZ, 7TV, with channel sets above global ones. If two emotes
 * share a name, the higher one wins.
 */
class EmoteRepository(
    private val providers: List<EmoteProvider>,
) {
    private val globalLock = Mutex()
    private var globalCache: List<Emote>? = null

    suspend fun indexFor(roomId: String?): EmoteIndex = coroutineScope {
        val globalJob = async { global() }
        val channelJob = async { if (roomId == null) emptyList() else channel(roomId) }
        val (global, channel) = awaitAll(globalJob, channelJob)
        EmoteIndex.Builder().addAll(global).addAll(channel).build()
    }

    private suspend fun global(): List<Emote> = globalLock.withLock {
        globalCache ?: collect { it.global() }.also { if (it.isNotEmpty()) globalCache = it }
    }

    private suspend fun channel(roomId: String): List<Emote> = collect { it.channel(roomId) }

    /** Runs [load] on every provider concurrently and orders the results by priority. */
    private suspend fun collect(load: suspend (EmoteProvider) -> List<Emote>): List<Emote> =
        coroutineScope {
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
