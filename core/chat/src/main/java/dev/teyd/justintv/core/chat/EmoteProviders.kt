package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.network.TextFetcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json =
    Json {
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
internal data class SevenTvSet(
    val emotes: List<SevenTvActive> = emptyList(),
)

@Serializable
internal data class SevenTvActive(
    val id: String,
    val name: String,
    val data: SevenTvData? = null,
)

@Serializable
internal data class SevenTvData(
    val host: SevenTvHost? = null,
)

@Serializable
internal data class SevenTvHost(
    val url: String = "",
    val files: List<SevenTvFile> = emptyList(),
)

@Serializable
internal data class SevenTvFile(
    val name: String = "",
    /** The single-frame file beside an animated one, for example `2x_static.webp`. */
    @SerialName("static_name") val staticName: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("frame_count") val frameCount: Int = 1,
)

@Serializable
internal data class SevenTvUser(
    @SerialName("emote_set") val emoteSet: SevenTvSet? = null,
)

object SevenTvParser {
    fun parseSet(body: String): List<Emote> = toEmotes(json.decodeFromString<SevenTvSet>(body))

    fun parseUser(body: String): List<Emote> =
        json
            .decodeFromString<SevenTvUser>(body)
            .emoteSet
            ?.let(::toEmotes)
            .orEmpty()

    private fun toEmotes(set: SevenTvSet): List<Emote> =
        set.emotes.mapNotNull { active ->
            val host = active.data?.host ?: return@mapNotNull null
            if (host.url.isBlank()) return@mapNotNull null
            val base = if (host.url.startsWith("//")) "https:${host.url}" else host.url
            val file =
                host.files.firstOrNull { it.name == "2x.webp" }
                    ?: host.files.firstOrNull { it.name.endsWith(".webp") }
            // An animated emote averages about 190 KB and a still frame of it about 2 KB, so grids
            // use the still. A static emote has no separate still; it is its own.
            val still =
                file
                    ?.takeIf { it.frameCount > 1 && it.staticName.isNotBlank() && it.staticName != it.name }
                    ?.let { "$base/${it.staticName}" }
            Emote(
                name = active.name,
                url = "$base/${file?.name ?: "2x.webp"}",
                width = file?.width?.takeIf { it > 0 },
                height = file?.height?.takeIf { it > 0 },
                source = EmoteSource.SevenTv,
                stillUrl = still,
            )
        }
}

class SevenTvProvider(
    private val fetcher: TextFetcher,
) : EmoteProvider {
    override val source = EmoteSource.SevenTv

    override suspend fun global(): List<Emote> = SevenTvParser.parseSet(fetcher.fetchText("https://7tv.io/v3/emote-sets/global"))

    override suspend fun channel(roomId: String): List<Emote> =
        SevenTvParser.parseUser(fetcher.fetchText("https://7tv.io/v3/users/twitch/$roomId"))
}

// ---------------------------------------------------------------- BTTV

@Serializable
internal data class BttvEmote(
    val id: String,
    val code: String,
    /** "gif" for animated emotes; those have a much smaller static PNG beside them. */
    val imageType: String = "",
)

@Serializable
internal data class BttvUser(
    val channelEmotes: List<BttvEmote> = emptyList(),
    val sharedEmotes: List<BttvEmote> = emptyList(),
)

object BttvParser {
    fun parseGlobal(body: String): List<Emote> = json.decodeFromString<List<BttvEmote>>(body).map(::toEmote)

    fun parseUser(body: String): List<Emote> {
        val user = json.decodeFromString<BttvUser>(body)
        return (user.sharedEmotes + user.channelEmotes).map(::toEmote)
    }

    private fun toEmote(emote: BttvEmote): Emote {
        val base = "https://cdn.betterttv.net/emote/${emote.id}"
        val animated = emote.imageType.equals("gif", ignoreCase = true)
        return Emote(
            name = emote.code,
            // An animated emote can be 3 MB as webp; the static PNG next to it is a few KB.
            url = "$base/2x.png",
            source = EmoteSource.Bttv,
            stillUrl = if (animated) "$base/2x.png" else null,
        )
    }
}

class BttvProvider(
    private val fetcher: TextFetcher,
) : EmoteProvider {
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
internal data class FfzSet(
    val emoticons: List<FfzEmote> = emptyList(),
)

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
        json
            .decodeFromString<FfzResponse>(body)
            .sets.values
            .flatMap { it.emoticons }
            .mapNotNull(::toEmote)

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

class FfzProvider(
    private val fetcher: TextFetcher,
) : EmoteProvider {
    override val source = EmoteSource.Ffz

    override suspend fun global(): List<Emote> = FfzParser.parseGlobal(fetcher.fetchText("https://api.frankerfacez.com/v1/set/global"))

    override suspend fun channel(roomId: String): List<Emote> =
        FfzParser.parseRoom(fetcher.fetchText("https://api.frankerfacez.com/v1/room/id/$roomId"))
}

// ---------------------------------------------------------------- Twitch

@Serializable
internal data class TwitchEmoteBody(
    val data: List<TwitchHelixEmote> = emptyList(),
)

@Serializable
internal data class TwitchHelixEmote(
    val id: String = "",
    val name: String = "",
    val format: List<String> = emptyList(),
)

object TwitchEmoteParser {
    fun parse(body: String): List<Emote> = json.decodeFromString<TwitchEmoteBody>(body).data.mapNotNull(::toEmote)

    /** Static frame for grids. `default` is what chat already uses, and it may be an animated GIF. */
    internal fun stillUrl(id: String): String = cdn(id, "static", "3.0")

    internal fun chatUrl(id: String): String = cdn(id, "default", "2.0")

    private fun toEmote(emote: TwitchHelixEmote): Emote? {
        if (emote.id.isBlank() || emote.name.isBlank()) return null
        return Emote(
            name = emote.name,
            url = chatUrl(emote.id),
            source = EmoteSource.Twitch,
            stillUrl = stillUrl(emote.id),
        )
    }

    private fun cdn(
        id: String,
        format: String,
        scale: String,
    ): String = "https://static-cdn.jtvnw.net/emoticons/v2/$id/$format/dark/$scale"
}

/**
 * Helix global and channel emotes. Needs a user or app token; a missing token contributes
 * nothing, the same as a provider that is down. No extra scope: channel emotes are the
 * broadcaster's set, not the signed-in user's unlocked set.
 */
class TwitchEmoteProvider(
    private val clientId: String,
    private val token: suspend () -> String?,
    private val fetch: suspend (url: String, clientId: String, accessToken: String) -> String,
) : EmoteProvider {
    override val source = EmoteSource.Twitch

    override suspend fun global(): List<Emote> = load("https://api.twitch.tv/helix/chat/emotes/global")

    override suspend fun channel(roomId: String): List<Emote> {
        if (roomId.isBlank()) return emptyList()
        val id = java.net.URLEncoder.encode(roomId, Charsets.UTF_8)
        return load("https://api.twitch.tv/helix/chat/emotes?broadcaster_id=$id")
    }

    private suspend fun load(url: String): List<Emote> {
        if (clientId.isBlank()) return emptyList()
        val access = token() ?: return emptyList()
        return TwitchEmoteParser.parse(fetch(url, clientId, access))
    }
}
