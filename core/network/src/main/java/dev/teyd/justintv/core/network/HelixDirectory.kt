package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.Game
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.model.twitchImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/** One page of a cursor-paginated list. [cursor] is null on the last page. */
data class Page<T>(
    val items: List<T>,
    val cursor: String? = null,
)

/** A Helix call failed in a way the caller may show, or fall back from. */
open class HelixException(
    message: String,
) : Exception(message)

/** Helix answered 429 and waiting once did not help (or the wait was too long). */
class HelixRateLimitedException : HelixException("Twitch is rate limiting requests. Try again shortly.")

/** Helix browse data. Not a [DirectorySource]: it has no channel search and no viewer totals for games. */
interface HelixDirectory {
    suspend fun streams(
        languages: Set<String>,
        gameId: String? = null,
        after: String? = null,
    ): Page<LiveStream>

    /** Viewer counts are not part of this endpoint, so every [Game.viewerCount] is 0. */
    suspend fun topGames(after: String? = null): Page<Game>

    suspend fun gameId(gameName: String): String?

    suspend fun followedStreams(after: String? = null): Page<LiveStream>
}

/** URLs and response mapping for Helix browse calls. Pure functions, covered by unit tests. */
object HelixDirectoryMapper {
    const val HELIX_URL = "https://api.twitch.tv/helix"
    const val MAX_PAGE = 100
    const val MAX_USER_IDS = 100
    const val MAX_LANGUAGES = 100

    private val json = Json { ignoreUnknownKeys = true }
    private val languageCode = Regex("[a-z]{2,3}")

    /**
     * App language names (`EN`, `PT_BR`) as Helix ISO 639-1 codes (`en`, `pt`). Twitch's own
     * stream language is the base code, so regional variants collapse and duplicates drop.
     */
    fun languageCodes(languages: Collection<String>): List<String> =
        languages
            .map {
                it
                    .trim()
                    .substringBefore('_')
                    .substringBefore('-')
                    .lowercase()
            }.filter { languageCode.matches(it) }
            .distinct()
            .take(MAX_LANGUAGES)

    fun streamsUrl(
        languages: Collection<String>,
        gameId: String? = null,
        after: String? = null,
        first: Int = MAX_PAGE,
    ): String =
        url(
            "streams",
            listOf("first" to first.coerceIn(1, MAX_PAGE).toString()) +
                languageCodes(languages).map { "language" to it } +
                listOfNotNull(gameId?.takeIf { it.isNotBlank() }?.let { "game_id" to it }) +
                afterParam(after),
        )

    fun followedUrl(
        userId: String,
        after: String? = null,
        first: Int = MAX_PAGE,
    ): String =
        url(
            "streams/followed",
            listOf("user_id" to userId, "first" to first.coerceIn(1, MAX_PAGE).toString()) + afterParam(after),
        )

    fun topGamesUrl(
        after: String? = null,
        first: Int = MAX_PAGE,
    ): String = url("games/top", listOf("first" to first.coerceIn(1, MAX_PAGE).toString()) + afterParam(after))

    fun gameByNameUrl(name: String): String = url("games", listOf("name" to name))

    /** Null when there is nothing to ask. Ids beyond the Helix limit are dropped. */
    fun usersUrl(ids: Collection<String>): String? {
        val unique = ids.filter { it.isNotBlank() }.distinct().take(MAX_USER_IDS)
        if (unique.isEmpty()) return null
        return url("users", unique.map { "id" to it })
    }

    fun parseStreams(
        body: String,
        avatars: Map<String, String> = emptyMap(),
    ): Page<LiveStream> {
        val parsed = decode<HelixStreamsBody>(body)
        return Page(
            items =
                parsed.data.map { stream ->
                    LiveStream(
                        id = stream.id,
                        login = stream.userLogin,
                        displayName = stream.userName?.takeIf { it.isNotBlank() } ?: stream.userLogin,
                        title = stream.title.orEmpty(),
                        viewerCount = stream.viewerCount,
                        previewUrl = twitchImageUrl(stream.thumbnailUrl),
                        avatarUrl = avatars[stream.userId],
                        gameName = stream.gameName?.takeIf { it.isNotBlank() },
                        language = stream.language?.takeIf { it.isNotBlank() }?.uppercase(),
                        startedAt = stream.startedAt,
                    )
                },
            cursor = parsed.pagination?.cursor?.takeIf { it.isNotBlank() },
        )
    }

    /** The user ids of a streams page, in order, for the avatar call. */
    fun streamUserIds(body: String): List<String> = decode<HelixStreamsBody>(body).data.map { it.userId }

    fun parseGames(body: String): Page<Game> {
        val parsed = decode<GamesBody>(body)
        return Page(
            items = parsed.data.map { it.toGame() },
            cursor = parsed.pagination?.cursor?.takeIf { it.isNotBlank() },
        )
    }

    fun parseGameId(body: String): String? = decode<GamesBody>(body).data.firstOrNull()?.id

    fun parseAvatars(body: String): Map<String, String> =
        decode<HelixUsersBody>(body)
            .data
            .mapNotNull { user -> user.profileImageUrl?.takeIf { it.isNotBlank() }?.let { user.id to it } }
            .toMap()

    private fun HelixGameBody.toGame() =
        Game(
            id = id,
            name = name,
            displayName = name,
            boxArtUrl = twitchImageUrl(boxArtUrl, width = 285, height = 380),
            viewerCount = 0,
        )

    private fun afterParam(after: String?): List<Pair<String, String>> =
        listOfNotNull(after?.takeIf { it.isNotBlank() }?.let { "after" to it })

    private fun url(
        path: String,
        params: List<Pair<String, String>>,
    ): String = "$HELIX_URL/$path?" + params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (e: Exception) {
            throw HelixException("Unreadable response from Twitch")
        }
}

/** When to retry a 429. Pure, so the arithmetic is testable without a clock or a network. */
object HelixRateLimit {
    const val MAX_WAIT_MS = 10_000L

    /**
     * Milliseconds to wait before the single retry, from `Retry-After` (seconds) or
     * `Ratelimit-Reset` (epoch seconds). Null when neither header is usable or the wait would
     * exceed [maxWaitMs]: the caller should fail instead of stalling the screen.
     */
    fun retryDelayMs(
        retryAfter: String?,
        resetEpochSeconds: String?,
        nowMs: Long,
        maxWaitMs: Long = MAX_WAIT_MS,
    ): Long? {
        val wait =
            retryAfter?.trim()?.toLongOrNull()?.let { it * 1000L }
                ?: resetEpochSeconds?.trim()?.toLongOrNull()?.let { it * 1000L - nowMs }
                ?: return null
        if (wait > maxWaitMs) return null
        return wait.coerceAtLeast(0L)
    }
}

/** A Helix HTTP answer, reduced to what the retry logic needs. */
data class HelixHttpResult(
    val code: Int,
    val body: String,
    val retryAfter: String? = null,
    val resetEpochSeconds: String? = null,
)

/**
 * Runs [call], and on a 429 waits as Twitch asks and tries once more. A second 429, or a wait
 * that is too long, throws [HelixRateLimitedException]. 401 throws [UnauthorizedException];
 * other non-200 codes throw [HelixException] with Twitch's message.
 */
internal suspend fun helixRequest(
    nowMs: () -> Long = System::currentTimeMillis,
    sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    call: suspend () -> HelixHttpResult,
): String {
    var result = call()
    if (result.code == 429) {
        val wait =
            HelixRateLimit.retryDelayMs(result.retryAfter, result.resetEpochSeconds, nowMs())
                ?: throw HelixRateLimitedException()
        sleep(wait)
        result = call()
        if (result.code == 429) throw HelixRateLimitedException()
    }
    if (result.code == 401) throw UnauthorizedException()
    if (result.code != 200) throw HelixException(errorMessage(result.body) ?: "Twitch returned ${result.code}")
    if (result.body.isBlank()) throw HelixException("Twitch returned an empty response")
    return result.body
}

@Serializable
private data class GamesBody(
    val data: List<HelixGameBody> = emptyList(),
    val pagination: HelixPaginationBody? = null,
)

@Serializable
private data class HelixGameBody(
    val id: String,
    val name: String,
    @SerialName("box_art_url") val boxArtUrl: String? = null,
)
