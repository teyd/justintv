package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatBadge
import dev.teyd.justintv.core.model.ChatBadgeSource
import dev.teyd.justintv.core.network.TextFetcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val badgesJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

// ---------------------------------------------------------------- Twitch

@Serializable
internal data class TwitchBadgeBody(
    val data: TwitchBadgeDataBody? = null,
    val errors: List<JsonElement> = emptyList(),
)

@Serializable
internal data class TwitchBadgeDataBody(
    val badges: List<TwitchBadgeVersionBody>? = null,
    val user: TwitchBadgeUserBody? = null,
)

@Serializable
internal data class TwitchBadgeUserBody(
    val broadcastBadges: List<TwitchBadgeVersionBody>? = null,
)

@Serializable
internal data class TwitchBadgeVersionBody(
    @SerialName("setID") val setId: String = "",
    val version: String = "",
    @SerialName("imageURL") val imageUrl: String = "",
    val title: String = "",
)

object TwitchBadgeParser {
    /** GraphQL's setID/version pair is what the IRC `badges` tag carries. */
    fun parse(
        body: String,
        channel: Boolean = false,
    ): Map<String, ChatBadge> {
        val response = badgesJson.decodeFromString<TwitchBadgeBody>(body)
        check(response.errors.isEmpty()) { "Twitch badge query failed" }
        val data = checkNotNull(response.data) { "Missing Twitch badge data" }
        val definitions =
            if (channel) {
                // An unknown/deleted channel is a valid null user, not a request failure.
                if (data.user == null) return emptyMap()
                checkNotNull(data.user.broadcastBadges) { "Missing Twitch channel badges" }
            } else {
                checkNotNull(data.badges) { "Missing Twitch global badges" }
            }
        val badges = LinkedHashMap<String, ChatBadge>()
        for (badge in definitions) {
            if (badge.setId.isBlank() || badge.version.isBlank() || badge.imageUrl.isBlank()) continue
            badges[BadgeIndex.key(badge.setId, badge.version)] =
                ChatBadge(
                    source = ChatBadgeSource.Twitch,
                    url = badge.imageUrl,
                    title = badge.title.ifBlank { badge.setId },
                )
        }
        return badges
    }
}

/**
 * Anonymous Twitch GraphQL global and channel badge sets. Never uses user credentials or
 * falls back to Helix. The repository absorbs network and GraphQL errors so chat continues.
 */
class TwitchBadgeProvider(
    private val post: suspend (body: String) -> String,
) : BadgeProvider {
    override val source = ChatBadgeSource.Twitch

    override suspend fun global(): BadgeData = load("query { badges { $FIELDS } }")

    override suspend fun channel(roomId: String): BadgeData {
        if (roomId.isBlank()) return BadgeData()
        val id = JsonPrimitive(roomId)
        return load("query { user(id: $id) { broadcastBadges { $FIELDS } } }", channel = true)
    }

    private suspend fun load(
        query: String,
        channel: Boolean = false,
    ): BadgeData {
        val body = buildJsonObject { put("query", JsonPrimitive(query)) }.toString()
        return BadgeData(sets = TwitchBadgeParser.parse(post(body), channel))
    }

    private companion object {
        // 72px covers the 18sp badge on high-density phones.
        const val FIELDS = "setID version title imageURL(size: QUADRUPLE)"
    }
}

// ---------------------------------------------------------------- Chatterino

@Serializable
internal data class ChatterinoBadgeBody(
    val badges: List<ChatterinoBadgeBodyBadge> = emptyList(),
)

@Serializable
internal data class ChatterinoBadgeBodyBadge(
    val tooltip: String = "",
    val image1: String = "",
    val image2: String = "",
    val image3: String = "",
    val users: List<String> = emptyList(),
)

object ChatterinoBadgeParser {
    fun parse(body: String): Map<String, List<ChatBadge>> {
        val users = HashMap<String, MutableList<ChatBadge>>()
        for (badge in badgesJson.decodeFromString<ChatterinoBadgeBody>(body).badges) {
            val url =
                listOf(badge.image3, badge.image2, badge.image1)
                    .firstOrNull { it.isNotBlank() } ?: continue
            val chatBadge =
                ChatBadge(
                    source = ChatBadgeSource.Chatterino,
                    url = url,
                    title = badge.tooltip.ifBlank { "Chatterino" },
                )
            for (user in badge.users) {
                if (user.isBlank()) continue
                users.getOrPut(user) { mutableListOf() } += chatBadge
            }
        }
        return users
    }
}

class ChatterinoBadgeProvider(
    private val fetcher: TextFetcher,
) : BadgeProvider {
    override val source = ChatBadgeSource.Chatterino

    override suspend fun global(): BadgeData =
        BadgeData(users = ChatterinoBadgeParser.parse(fetcher.fetchText("https://api.chatterino.com/badges")))
}

// ---------------------------------------------------------------- FFZ

@Serializable
internal data class FfzBadgesBody(
    val badges: List<FfzBadgeBody> = emptyList(),
    val users: Map<String, List<Long>> = emptyMap(),
)

@Serializable
internal data class FfzBadgeBody(
    val id: Int = 0,
    val name: String = "",
    val title: String = "",
    val urls: Map<String, String> = emptyMap(),
)

object FfzBadgeParser {
    fun parse(body: String): Map<String, List<ChatBadge>> {
        val response = badgesJson.decodeFromString<FfzBadgesBody>(body)
        val definitions = response.badges.associateBy { it.id }
        val users = HashMap<String, MutableList<ChatBadge>>()
        for ((badgeId, userIds) in response.users) {
            val definition = definitions[badgeId.toIntOrNull()] ?: continue
            val raw =
                listOf("4", "2", "1")
                    .firstNotNullOfOrNull { scale -> definition.urls[scale]?.takeIf { it.isNotBlank() } }
                    ?: definition.urls.values.firstOrNull { it.isNotBlank() } ?: continue
            val chatBadge =
                ChatBadge(
                    source = ChatBadgeSource.Ffz,
                    url = if (raw.startsWith("//")) "https:$raw" else raw,
                    title = definition.title.ifBlank { definition.name },
                )
            for (userId in userIds) {
                users.getOrPut(userId.toString()) { mutableListOf() } += chatBadge
            }
        }
        return users
    }
}

class FfzBadgeProvider(
    private val fetcher: TextFetcher,
) : BadgeProvider {
    override val source = ChatBadgeSource.Ffz

    override suspend fun global(): BadgeData =
        BadgeData(users = FfzBadgeParser.parse(fetcher.fetchText("https://api.frankerfacez.com/v1/badges/ids")))
}

// ---------------------------------------------------------------- BTTV

@Serializable
internal data class BttvBadgeEntry(
    @SerialName("providerId") val providerId: String = "",
    val badge: BttvBadgeInfo? = null,
)

@Serializable
internal data class BttvBadgeInfo(
    val description: String = "",
    val svg: String = "",
)

object BttvBadgeParser {
    fun parse(body: String): Map<String, List<ChatBadge>> {
        val users = HashMap<String, MutableList<ChatBadge>>()
        for (entry in badgesJson.decodeFromString<List<BttvBadgeEntry>>(body)) {
            val info = entry.badge ?: continue
            if (entry.providerId.isBlank() || info.svg.isBlank()) continue
            users.getOrPut(entry.providerId) { mutableListOf() } +=
                ChatBadge(
                    source = ChatBadgeSource.Bttv,
                    url = info.svg,
                    title = info.description.ifBlank { "BetterTTV" },
                )
        }
        return users
    }
}

class BttvBadgeProvider(
    private val fetcher: TextFetcher,
) : BadgeProvider {
    override val source = ChatBadgeSource.Bttv

    override suspend fun global(): BadgeData =
        BadgeData(users = BttvBadgeParser.parse(fetcher.fetchText("https://api.betterttv.net/3/cached/badges/twitch")))
}
