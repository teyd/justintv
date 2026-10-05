package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.Game
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.model.twitchImageUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A directory request failed for a reason the UI should show. */
class DirectoryException(message: String) : Exception(message)

@Serializable
internal data class GqlResponse<T>(
    val data: T? = null,
    val errors: List<GqlError>? = null,
)

@Serializable
internal data class GqlError(val message: String = "")

@Serializable
internal data class Connection<T>(val edges: List<Edge<T>> = emptyList())

@Serializable
internal data class Edge<T>(val node: T)

@Serializable
internal data class StreamNode(
    val id: String,
    val title: String? = null,
    val viewersCount: Int = 0,
    val createdAt: String? = null,
    val previewImageURL: String? = null,
    val broadcaster: BroadcasterNode? = null,
    val game: GameNode? = null,
)

@Serializable
internal data class BroadcasterNode(
    val login: String,
    val displayName: String? = null,
    val profileImageURL: String? = null,
    val broadcastSettings: BroadcastSettingsNode? = null,
)

@Serializable
internal data class BroadcastSettingsNode(val language: String? = null)

@Serializable
internal data class GameNode(
    val id: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val boxArtURL: String? = null,
    val viewersCount: Int? = null,
)

@Serializable
internal data class TopStreamsData(val streams: Connection<StreamNode>? = null)

@Serializable
internal data class TopGamesData(val games: Connection<GameNode>? = null)

@Serializable
internal data class ChannelStreamData(val user: ChannelUser? = null)

@Serializable
internal data class ChannelUser(val stream: ChannelStream? = null)

@Serializable
internal data class ChannelStream(val viewersCount: Int = 0, val createdAt: String? = null)

/** What the player shows about a live channel, refreshed while it plays. */
data class ChannelLive(val viewers: Int, val startedAt: String?)

@Serializable
internal data class GameStreamsData(val game: GameWithStreams? = null)

@Serializable
internal data class GameWithStreams(val streams: Connection<StreamNode>? = null)

/** Turns GraphQL responses into app models. Pure functions, covered by unit tests. */
object DirectoryParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseTopStreams(body: String): List<LiveStream> =
        decode<TopStreamsData>(body).streams.toLiveStreams()

    fun parseGameStreams(body: String): List<LiveStream> =
        decode<GameStreamsData>(body).game?.streams.toLiveStreams()

    /** Null when the channel is offline or unknown. */
    fun parseChannelLive(body: String): ChannelLive? =
        decode<ChannelStreamData>(body).user?.stream?.let { ChannelLive(it.viewersCount, it.createdAt) }

    fun parseTopGames(body: String): List<Game> =
        decode<TopGamesData>(body).games?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node
            val name = node.name ?: return@mapNotNull null
            Game(
                id = node.id ?: name,
                name = name,
                displayName = node.displayName ?: name,
                boxArtUrl = node.boxArtURL,
                viewerCount = node.viewersCount ?: 0,
            )
        }

    private inline fun <reified T> decode(body: String): T {
        val response = try {
            json.decodeFromString<GqlResponse<T>>(body)
        } catch (e: Exception) {
            throw DirectoryException("Unreadable response from Twitch")
        }
        response.errors?.firstOrNull()?.let { error ->
            throw DirectoryException(
                if (error.message.contains("integrity", ignoreCase = true)) {
                    "Twitch limits anonymous browsing. Log in to see more."
                } else {
                    error.message.ifBlank { "Twitch rejected the request" }
                },
            )
        }
        return response.data ?: throw DirectoryException("Twitch returned no data")
    }

    private fun Connection<StreamNode>?.toLiveStreams(): List<LiveStream> =
        this?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node
            val broadcaster = node.broadcaster ?: return@mapNotNull null
            LiveStream(
                id = node.id,
                login = broadcaster.login,
                displayName = broadcaster.displayName ?: broadcaster.login,
                title = node.title.orEmpty(),
                viewerCount = node.viewersCount,
                previewUrl = twitchImageUrl(node.previewImageURL),
                avatarUrl = broadcaster.profileImageURL,
                gameName = node.game?.displayName ?: node.game?.name,
                language = broadcaster.broadcastSettings?.language,
                startedAt = node.createdAt,
            )
        }
}

/** Source of browse data. An interface so view models can be tested with fakes. */
interface DirectorySource {
    suspend fun topStreams(languages: Set<String>): List<LiveStream>
    suspend fun gameStreams(gameName: String, languages: Set<String>): List<LiveStream>
    suspend fun topGames(): List<Game>

    /** Current viewers and start time, or null when the channel is not live. */
    suspend fun channelLive(login: String): ChannelLive?
}

/** Anonymous directory over GraphQL. See [DirectoryQueries] for its limits. */
class TwitchDirectoryApi(private val gql: GqlClient) : DirectorySource {

    override suspend fun topStreams(languages: Set<String>): List<LiveStream> =
        DirectoryParser.parseTopStreams(request(DirectoryQueries.topStreams(languages)))

    override suspend fun gameStreams(gameName: String, languages: Set<String>): List<LiveStream> =
        DirectoryParser.parseGameStreams(request(DirectoryQueries.gameStreams(gameName, languages)))

    override suspend fun topGames(): List<Game> =
        DirectoryParser.parseTopGames(request(DirectoryQueries.topGames()))

    override suspend fun channelLive(login: String): ChannelLive? =
        DirectoryParser.parseChannelLive(request(DirectoryQueries.channelStream(login)))

    private suspend fun request(query: String): String {
        val body = kotlinx.serialization.json.buildJsonObject {
            put("query", kotlinx.serialization.json.JsonPrimitive(query))
        }.toString()
        return try {
            gql.post(body)
        } catch (e: PlaybackException) {
            throw DirectoryException(e.message ?: "Could not reach Twitch")
        }
    }
}
