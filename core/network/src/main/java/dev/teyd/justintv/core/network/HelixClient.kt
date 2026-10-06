package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.model.twitchImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Twitch Helix calls made with the signed-in user's token.
 *
 * Every call sends the Client-Id and a Bearer header. A 401 throws [UnauthorizedException].
 */
class HelixClient(
    private val http: OkHttpClient,
) {
    /**
     * GET [url] and return the body. A non-200 throws [IdentityException] with Twitch's own
     * message, or [failureMessage] when Twitch gave none.
     */
    suspend fun authorizedGet(
        url: String,
        clientId: String,
        accessToken: String,
        failureMessage: String,
    ): String {
        val result = send(url, clientId, accessToken)
        if (result.code == 401) throw UnauthorizedException()
        if (result.code != 200) throw IdentityException(errorMessage(result.body) ?: failureMessage)
        if (result.body.isBlank()) throw IdentityException("empty response")
        return result.body
    }

    /** The raw answer, with the headers the rate-limit retry needs. Status handling is the caller's. */
    internal suspend fun send(
        url: String,
        clientId: String,
        accessToken: String,
    ): HelixHttpResult {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Client-Id", clientId)
                .build()
        return http.newCall(request).awaitResponse().use { response ->
            HelixHttpResult(
                code = response.code,
                body = response.body?.string().orEmpty(),
                retryAfter = response.header("Retry-After"),
                resetEpochSeconds = response.header("Ratelimit-Reset"),
            )
        }
    }

    suspend fun currentUser(
        clientId: String,
        accessToken: String,
    ): TwitchUser {
        val body = authorizedGet("$HELIX_URL/users", clientId, accessToken, "Could not load the account")
        val user =
            twitchJson.decodeFromString<HelixUsersBody>(body).data.firstOrNull()
                ?: throw IdentityException("Twitch returned no account")
        return TwitchUser(
            id = user.id,
            login = user.login,
            displayName = user.displayName ?: user.login,
        )
    }

    suspend fun followedStreams(
        clientId: String,
        accessToken: String,
        userId: String,
    ): List<LiveStream> {
        val url = "$HELIX_URL/streams/followed?user_id=${encode(userId)}&first=100"
        val body = authorizedGet(url, clientId, accessToken, "Could not load followed streams")
        val streams = twitchJson.decodeFromString<HelixStreamsBody>(body).data
        if (streams.isEmpty()) return emptyList()
        // A stream object has no picture; one extra call fills the avatars for the whole page.
        val avatars =
            try {
                userAvatars(clientId, accessToken, streams.map { it.userId })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
        return streams.map { it.toLiveStream(avatarUrl = avatars[it.userId]) }
    }

    suspend fun userAvatars(
        clientId: String,
        accessToken: String,
        ids: List<String>,
    ): Map<String, String> {
        val unique = ids.filter { it.isNotBlank() }.distinct().take(MAX_USER_IDS)
        if (unique.isEmpty()) return emptyMap()
        val query = unique.joinToString("&") { "id=${encode(it)}" }
        val body = authorizedGet("$HELIX_URL/users?$query", clientId, accessToken, "Could not load channel pictures")
        return twitchJson
            .decodeFromString<HelixUsersBody>(body)
            .data
            .mapNotNull { user -> user.profileImageUrl?.let { user.id to it } }
            .toMap()
    }

    private companion object {
        const val HELIX_URL = "https://api.twitch.tv/helix"
        const val MAX_USER_IDS = 100

        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    }
}

@Serializable
internal data class HelixUsersBody(
    val data: List<HelixUserBody> = emptyList(),
)

@Serializable
internal data class HelixUserBody(
    val id: String,
    val login: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
)

@Serializable
internal data class HelixStreamsBody(
    val data: List<HelixStreamBody> = emptyList(),
    val pagination: HelixPaginationBody? = null,
)

@Serializable
internal data class HelixPaginationBody(
    val cursor: String? = null,
)

@Serializable
internal data class HelixStreamBody(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("user_login") val userLogin: String,
    @SerialName("user_name") val userName: String? = null,
    val title: String? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("game_name") val gameName: String? = null,
    val language: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
)

private fun HelixStreamBody.toLiveStream(avatarUrl: String? = null) =
    LiveStream(
        id = id,
        login = userLogin,
        displayName = userName ?: userLogin,
        title = title.orEmpty(),
        viewerCount = viewerCount,
        previewUrl = twitchImageUrl(thumbnailUrl),
        avatarUrl = avatarUrl,
        gameName = gameName,
        language = language,
        startedAt = startedAt,
    )
