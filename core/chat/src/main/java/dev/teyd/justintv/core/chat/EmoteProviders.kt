package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.network.TextFetcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** One emote provider. Both calls throw on network or parse errors; the repository absorbs them. */
interface EmoteProvider {
    /** Which provider this is, for enable/disable filtering. */
    val source: EmoteSource

    suspend fun global(): List<Emote>
    suspend fun channel(roomId: String): List<Emote>
}

// ---------------------------------------------------------------- 7TV

@Serializable
internal data class SevenTvSet(val emotes: List<SevenTvActive> = emptyList())

@Serializable
internal data class SevenTvActive(
    val id: String,
    val name: String,
    val data: SevenTvData? = null,
)

@Serializable
internal data class SevenTvData(val host: SevenTvHost? = null)

@Serializable
internal data class SevenTvHost(val url: String = "", val files: List<SevenTvFile> = emptyList())

@Serializable
internal data class SevenTvFile(val name: String = "", val width: Int = 0, val height: Int = 0)

@Serializable
internal data class SevenTvUser(@SerialName("emote_set") val emoteSet: SevenTvSet? = null)

object SevenTvParser {
    fun parseSet(body: String): List<Emote> = toEmotes(json.decodeFromString<SevenTvSet>(body))

    fun parseUser(body: String): List<Emote> =
        json.decodeFromString<SevenTvUser>(body).emoteSet?.let(::toEmotes).orEmpty()

    private fun toEmotes(set: SevenTvSet): List<Emote> = set.emotes.mapNotNull { active ->
        val host = active.data?.host ?: return@mapNotNull null
        if (host.url.isBlank()) return@mapNotNull null
        val base = if (host.url.startsWith("//")) "https:${host.url}" else host.url
        val file = host.files.firstOrNull { it.name == "2x.webp" }
            ?: host.files.firstOrNull { it.name.endsWith(".webp") }
        Emote(
            name = active.name,
            url = "$base/${file?.name ?: "2x.webp"}",
            width = file?.width?.takeIf { it > 0 },
            height = file?.height?.takeIf { it > 0 },
            source = EmoteSource.SevenTv,
        )
    }
}

class SevenTvProvider(private val fetcher: TextFetcher) : EmoteProvider {
    override val source = EmoteSource.SevenTv

    override suspend fun global(): List<Emote> =
        SevenTvParser.parseSet(fetcher.fetchText("https://7tv.io/v3/emote-sets/global"))

    override suspend fun channel(roomId: String): List<Emote> =
        SevenTvParser.parseUser(fetcher.fetchText("https://7tv.io/v3/users/twitch/$roomId"))
}

// ---------------------------------------------------------------- BTTV

@Serializable
internal data class BttvEmote(val id: String, val code: String)

@Serializable
internal data class BttvUser(
    val channelEmotes: List<BttvEmote> = emptyList(),
    val sharedEmotes: List<BttvEmote> = emptyList(),
)

object BttvParser {
    fun parseGlobal(body: String): List<Emote> =
        json.decodeFromString<List<BttvEmote>>(body).map(::toEmote)

    fun parseUser(body: String): List<Emote> {
        val user = json.decodeFromString<BttvUser>(body)
        return (user.sharedEmotes + user.channelEmotes).map(::toEmote)
    }

    private fun toEmote(emote: BttvEmote) = Emote(
        name = emote.code,
        url = "https://cdn.betterttv.net/emote/${emote.id}/2x.webp",
        source = EmoteSource.Bttv,
    )
}

class BttvProvider(private val fetcher: TextFetcher) : EmoteProvider {
    override val source = EmoteSource.Bttv

    override suspend fun global(): List<Emote> =
        BttvParser.parseGlobal(fetcher.fetchText("https://api.betterttv.net/3/cached/emotes/global"))

    override suspend fun channel(roomId: String): List<Emote> =
        BttvParser.parseUser(fetcher.fetchText("https://api.betterttv.net/3/cached/users/twitch/$roomId"))
}

// ---------------------------------------------------------------- FFZ

@Serializable
internal data class FfzResponse(
    @SerialName("default_sets") val defaultSets: List<Int> = emptyList(),
    val sets: Map<String, FfzSet> = emptyMap(),
)

@Serializable
internal data class FfzSet(val emoticons: List<FfzEmote> = emptyList())

@Serializable
internal data class FfzEmote(
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val urls: Map<String, String> = emptyMap(),
)

object FfzParser {
    /** The global response lists many sets; only the default ones apply to everyone. */
    fun parseGlobal(body: String): List<Emote> {
        val response = json.decodeFromString<FfzResponse>(body)
        val wanted = response.defaultSets.map { it.toString() }.toSet()
        return response.sets
            .filterKeys { it in wanted }
            .values
            .flatMap { it.emoticons }
            .mapNotNull(::toEmote)
    }

    /** A room response contains only that room's sets. */
    fun parseRoom(body: String): List<Emote> =
        json.decodeFromString<FfzResponse>(body).sets.values.flatMap { it.emoticons }.mapNotNull(::toEmote)

    private fun toEmote(emote: FfzEmote): Emote? {
        val raw = emote.urls["2"] ?: emote.urls["1"] ?: emote.urls.values.firstOrNull() ?: return null
        return Emote(
            name = emote.name,
            url = if (raw.startsWith("//")) "https:$raw" else raw,
            width = emote.width.takeIf { it > 0 },
            height = emote.height.takeIf { it > 0 },
            source = EmoteSource.Ffz,
        )
    }
}

class FfzProvider(private val fetcher: TextFetcher) : EmoteProvider {
    override val source = EmoteSource.Ffz

    override suspend fun global(): List<Emote> =
        FfzParser.parseGlobal(fetcher.fetchText("https://api.frankerfacez.com/v1/set/global"))

    override suspend fun channel(roomId: String): List<Emote> =
        FfzParser.parseRoom(fetcher.fetchText("https://api.frankerfacez.com/v1/room/id/$roomId"))
}
