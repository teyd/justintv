package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatBadge
import dev.teyd.justintv.core.model.ChatBadgeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** What one badge provider contributes. */
data class BadgeData(
    /** Twitch badges, keyed by `"set/version"` from the IRC `badges` tag. */
    val sets: Map<String, ChatBadge> = emptyMap(),
    /** Third-party badges, keyed by the chatter's Twitch user id. */
    val users: Map<String, List<ChatBadge>> = emptyMap(),
) {
    val isEmpty: Boolean get() = sets.isEmpty() && users.isEmpty()
}

/** One source of badges. Calls throw on network or parse errors; the repository absorbs them. */
interface BadgeProvider {
    /** Which source this is, for enable/disable filtering. */
    val source: ChatBadgeSource

    /** Definitions that apply to every channel, or the whole user-to-badge map. */
    suspend fun global(): BadgeData

    /** Definitions for one channel. User-keyed sources have none. */
    suspend fun channel(roomId: String): BadgeData = BadgeData()
}

/**
 * A badge lookup for one channel: Twitch badges come from the `badges` tag, the others from
 * the chatter's Twitch user id.
 */
class BadgeIndex internal constructor(
    private val sets: Map<String, ChatBadge>,
    private val users: Map<String, List<ChatBadge>>,
    /** Whether 7TV badges are on. Those are resolved per user, not part of the index. */
    val sevenTv: Boolean,
) {
    fun twitch(
        setId: String,
        version: String,
    ): ChatBadge? = sets[key(setId, version)]

    fun user(userId: String): List<ChatBadge> = users[userId].orEmpty()

    internal companion object {
        val EMPTY = BadgeIndex(emptyMap(), emptyMap(), sevenTv = false)

        fun key(
            setId: String,
            version: String,
        ): String = "$setId/$version"
    }
}

/**
 * Builds the badge index for a channel from every enabled source.
 *
 * A provider that is down or has nothing contributes nothing; it never stops chat. Global
 * badge sets are cached per process, keyed by which providers are enabled, so toggling a
 * source in settings takes effect on the next index build without a stale cache. Twitch
 * channel badges are fetched per build, because they are the only per-channel set.
 */
class BadgeRepository(
    private val providers: List<BadgeProvider>,
    /** Re-read on every index build so settings changes apply immediately. */
    private val enabledSources: Flow<Set<ChatBadgeSource>> = flowOf(ChatBadgeSource.entries.toSet()),
) {
    private val globalLock = Mutex()
    private val globalBySource = HashMap<ChatBadgeSource, BadgeData>()

    suspend fun indexFor(roomId: String?): BadgeIndex =
        coroutineScope {
            val enabledSet = enabledSources.first()
            val enabled = providers.filter { it.source in enabledSet }
            val globalJob = async { global(enabled) }
            val channelJob = async { if (roomId == null) emptyList() else collect(enabled) { it.channel(roomId) } }
            val (global, channel) = awaitAll(globalJob, channelJob)
            build(enabledSet, global, channel)
        }

    /** Global first, then channel: a channel version overrides the global one for the same key. */
    private fun build(
        enabled: Set<ChatBadgeSource>,
        global: List<BadgeData>,
        channel: List<BadgeData>,
    ): BadgeIndex {
        val sets = LinkedHashMap<String, ChatBadge>()
        val users = HashMap<String, MutableList<ChatBadge>>()

        fun merge(data: BadgeData) {
            sets.putAll(data.sets)
            data.users.forEach { (userId, badges) -> users.getOrPut(userId) { mutableListOf() } += badges }
        }
        global.forEach(::merge)
        channel.forEach(::merge)
        return BadgeIndex(
            sets = sets,
            users = users.mapValues { (_, badges) -> badges.sortedBy { it.source.ordinal } },
            sevenTv = ChatBadgeSource.SevenTv in enabled,
        )
    }

    /**
     * Cached per provider, and only when the provider returned something. An empty Twitch set
     * (not signed in yet) must not stick, or the next channel open would skip it forever.
     */
    private suspend fun global(providers: List<BadgeProvider>): List<BadgeData> =
        globalLock.withLock {
            val missing = providers.filter { globalBySource[it.source] == null }
            if (missing.isNotEmpty()) {
                fetchEach(missing) { it.global() }.forEach { (source, data) ->
                    if (!data.isEmpty) globalBySource[source] = data
                }
            }
            providers.mapNotNull { globalBySource[it.source] }
        }

    private suspend fun fetchEach(
        providers: List<BadgeProvider>,
        load: suspend (BadgeProvider) -> BadgeData,
    ): List<Pair<ChatBadgeSource, BadgeData>> =
        coroutineScope {
            providers
                .map { provider ->
                    async {
                        val loaded =
                            try {
                                load(provider)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                BadgeData()
                            }
                        provider.source to loaded
                    }
                }.awaitAll()
        }

    private suspend fun collect(
        providers: List<BadgeProvider>,
        load: suspend (BadgeProvider) -> BadgeData,
    ): List<BadgeData> =
        coroutineScope {
            providers
                .map { provider ->
                    async {
                        try {
                            load(provider)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            BadgeData()
                        }
                    }
                }.awaitAll()
        }
}
