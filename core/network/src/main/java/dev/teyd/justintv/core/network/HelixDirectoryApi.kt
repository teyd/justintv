package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.model.Game
import dev.teyd.justintv.core.model.LiveStream
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.cancellation.CancellationException

/** What one Helix call needs from the signed-in user. */
data class HelixCredentials(
    val clientId: String,
    val accessToken: String,
    val userId: String,
)

/** Runs a Helix call with the user's token, refreshing and retrying once on a 401. */
interface HelixAuthorizer {
    val isSignedIn: Boolean

    suspend fun <T> authorized(block: suspend (HelixCredentials) -> T): T
}

/** [HelixDirectory] over the Helix REST API with the signed-in user's token. */
class HelixDirectoryApi(
    private val http: OkHttpClient,
    private val auth: HelixAuthorizer,
) : HelixDirectory {
    override suspend fun streams(
        languages: Set<String>,
        gameId: String?,
        after: String?,
    ): Page<LiveStream> =
        auth.authorized { creds ->
            streamsPage(creds, HelixDirectoryMapper.streamsUrl(languages, gameId, after))
        }

    override suspend fun followedStreams(after: String?): Page<LiveStream> =
        auth.authorized { creds ->
            streamsPage(creds, HelixDirectoryMapper.followedUrl(creds.userId, after))
        }

    override suspend fun topGames(after: String?): Page<Game> =
        auth.authorized { creds ->
            HelixDirectoryMapper.parseGames(get(creds, HelixDirectoryMapper.topGamesUrl(after)))
        }

    override suspend fun gameId(gameName: String): String? =
        auth.authorized { creds ->
            HelixDirectoryMapper.parseGameId(get(creds, HelixDirectoryMapper.gameByNameUrl(gameName)))
        }

    private suspend fun streamsPage(
        creds: HelixCredentials,
        url: String,
    ): Page<LiveStream> {
        val body = get(creds, url)
        val ids = HelixDirectoryMapper.streamUserIds(body)
        // A stream object has no picture; one extra call fills the avatars for the whole page.
        val avatars =
            try {
                HelixDirectoryMapper.usersUrl(ids)?.let { HelixDirectoryMapper.parseAvatars(get(creds, it)) }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
        return HelixDirectoryMapper.parseStreams(body, avatars)
    }

    private suspend fun get(
        creds: HelixCredentials,
        url: String,
    ): String =
        helixRequest {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${creds.accessToken}")
                    .header("Client-Id", creds.clientId)
                    .build()
            http.newCall(request).awaitResponse().use { response ->
                HelixHttpResult(
                    code = response.code,
                    body = response.body?.string().orEmpty(),
                    retryAfter = response.header("Retry-After"),
                    resetEpochSeconds = response.header("Ratelimit-Reset"),
                )
            }
        }
}

/**
 * Helix while signed in, anonymous GraphQL otherwise.
 *
 * Any Helix failure other than cancellation falls back to [gql], including an expired sign-in
 * (the session has already dropped it by then, so the user is browsing anonymously anyway) and
 * a second 429. Channel search and live checks always use [gql]: Helix search needs no extra
 * scope but returns no viewer counts per channel.
 */
class CompositeDirectorySource(
    private val gql: DirectorySource,
    private val helix: HelixDirectory,
    private val auth: HelixAuthorizer,
) : DirectorySource {
    override suspend fun topStreams(languages: Set<String>): List<LiveStream> = topStreamsPage(languages, null).items

    override suspend fun gameStreams(
        gameName: String,
        languages: Set<String>,
    ): List<LiveStream> = gameStreamsPage(gameName, languages, null).items

    override suspend fun topStreamsPage(
        languages: Set<String>,
        after: String?,
    ): Page<LiveStream> = viaHelix(after, fallback = { gql.topStreamsPage(languages, null) }) { helix.streams(languages, after = after) }

    override suspend fun gameStreamsPage(
        gameName: String,
        languages: Set<String>,
        after: String?,
    ): Page<LiveStream> =
        viaHelix(after, fallback = { gql.gameStreamsPage(gameName, languages, null) }) {
            val gameId = helix.gameId(gameName) ?: return@viaHelix Page(emptyList())
            helix.streams(languages, gameId = gameId, after = after)
        }

    /** GraphQL, not Helix: only GraphQL knows each game's viewer count, which orders the grid. */
    override suspend fun topGames(): List<Game> = gql.topGames()

    override suspend fun channelLive(login: String): ChannelLive? = gql.channelLive(login)

    override suspend fun searchChannels(query: String): List<ChannelHit> = gql.searchChannels(query)

    /**
     * A cursor only means something to Helix, so a failed follow-up page throws instead of
     * restarting from GraphQL's first page and repeating streams the user already saw.
     */
    private suspend fun <T> viaHelix(
        after: String?,
        fallback: suspend () -> T,
        call: suspend () -> T,
    ): T {
        if (after == null && !auth.isSignedIn) return fallback()
        return try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (after != null) throw e
            fallback()
        }
    }
}
