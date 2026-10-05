package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.model.twitchImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/** A signed-in Twitch user. The token itself never leaves the session store. */
data class TwitchUser(
    val id: String,
    val login: String,
    val displayName: String,
)

/** What the device-code screen shows, and what the poll needs. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

data class TokenGrant(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int,
    val scopes: List<String>,
)

/** Result of one device-code poll. Pending means wait and try again. */
sealed interface DevicePoll {
    data object Pending : DevicePoll

    data object SlowDown : DevicePoll

    data class Granted(
        val grant: TokenGrant,
    ) : DevicePoll

    data class Rejected(
        val message: String,
    ) : DevicePoll
}

open class IdentityException(
    message: String,
) : Exception(message)

/**
 * Twitch identity and Helix calls that carry a user token.
 *
 * This is not the GraphQL client. Playback tokens stay on [GqlClient], anonymous, and never
 * see what this class sends.
 */
class TwitchIdentityApi(
    private val http: OkHttpClient,
) {
    suspend fun requestDeviceCode(
        clientId: String,
        scopes: String,
    ): DeviceCode {
        val body = form("client_id" to clientId, "scopes" to scopes)
        val response = post(DEVICE_URL, body)
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Twitch refused the sign-in request")
        }
        val parsed = json.decodeFromString<DeviceCodeBody>(response.body)
        return DeviceCode(
            deviceCode = parsed.deviceCode,
            userCode = parsed.userCode,
            verificationUri = parsed.verificationUri.ifBlank { ACTIVATE_URL },
            expiresInSeconds = parsed.expiresIn.coerceAtLeast(1),
            intervalSeconds = parsed.interval.coerceAtLeast(1),
        )
    }

    suspend fun pollDeviceCode(
        clientId: String,
        scopes: String,
        deviceCode: String,
    ): DevicePoll {
        val body =
            form(
                "client_id" to clientId,
                "scopes" to scopes,
                "device_code" to deviceCode,
                "grant_type" to DEVICE_GRANT,
            )
        val response = post(TOKEN_URL, body)
        return parseDevicePoll(response.code, response.body)
    }

    suspend fun refresh(
        clientId: String,
        refreshToken: String,
    ): TokenGrant {
        val body =
            form(
                "client_id" to clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            )
        val response = post(TOKEN_URL, body)
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Could not refresh the sign-in")
        }
        return json.decodeFromString<TokenBody>(response.body).toGrant()
    }

    /** Null when the token is rejected. Other failures throw. */
    suspend fun validate(accessToken: String): TwitchUser? {
        val response = get(VALIDATE_URL, bearer = accessToken, clientId = null, oauthPrefix = "OAuth")
        if (response.code == 401) return null
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Could not check the sign-in")
        }
        val parsed = json.decodeFromString<ValidateBody>(response.body)
        val id = parsed.userId ?: return null
        val login = parsed.login ?: return null
        return TwitchUser(id = id, login = login, displayName = parsed.login)
    }

    suspend fun currentUser(
        clientId: String,
        accessToken: String,
    ): TwitchUser {
        val response = get("$HELIX_URL/users", bearer = accessToken, clientId = clientId, oauthPrefix = "Bearer")
        if (response.code == 401) throw UnauthorizedException()
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Could not load the account")
        }
        val user =
            json.decodeFromString<HelixUsersBody>(response.body).data.firstOrNull()
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
        val response = get(url, bearer = accessToken, clientId = clientId, oauthPrefix = "Bearer")
        if (response.code == 401) throw UnauthorizedException()
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Could not load followed streams")
        }
        val streams = json.decodeFromString<HelixStreamsBody>(response.body).data
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
        val response = get("$HELIX_URL/users?$query", bearer = accessToken, clientId = clientId, oauthPrefix = "Bearer")
        if (response.code == 401) throw UnauthorizedException()
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Could not load channel pictures")
        }
        return json
            .decodeFromString<HelixUsersBody>(response.body)
            .data
            .mapNotNull { user -> user.profileImageUrl?.let { user.id to it } }
            .toMap()
    }

    private suspend fun post(
        url: String,
        body: FormBody,
    ): HttpResult {
        val request =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()
        return execute(request)
    }

    private suspend fun get(
        url: String,
        bearer: String,
        clientId: String?,
        oauthPrefix: String,
    ): HttpResult {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "$oauthPrefix $bearer")
                .apply { if (clientId != null) header("Client-Id", clientId) }
                .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): HttpResult =
        http.newCall(request).awaitResponse().use { response ->
            HttpResult(response.code, response.body?.string().orEmpty())
        }

    private fun form(vararg fields: Pair<String, String>): FormBody =
        FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()

    private data class HttpResult(
        val code: Int,
        val body: String,
    )

    companion object {
        const val SCOPES = "user:read:follows"

        /** Follows plus the IRC scopes needed to send chat. Requested only when the user asks to chat. */
        const val CHAT_SCOPES = "user:read:follows chat:read chat:edit"
        private const val DEVICE_URL = "https://id.twitch.tv/oauth2/device"
        private const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
        private const val HELIX_URL = "https://api.twitch.tv/helix"
        private const val ACTIVATE_URL = "https://www.twitch.tv/activate"
        private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val MAX_USER_IDS = 100

        private val json = Json { ignoreUnknownKeys = true }

        fun parseDevicePoll(
            code: Int,
            body: String,
        ): DevicePoll {
            if (code == 200) {
                return DevicePoll.Granted(json.decodeFromString<TokenBody>(body).toGrant())
            }
            val message = errorMessage(body).orEmpty()
            return when {
                message.contains("authorization_pending", ignoreCase = true) -> DevicePoll.Pending
                message.contains("slow_down", ignoreCase = true) -> DevicePoll.SlowDown
                else -> DevicePoll.Rejected(message.ifBlank { "Sign-in was rejected" })
            }
        }

        private fun errorMessage(body: String): String? =
            try {
                json.decodeFromString<ErrorBody>(body).message?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    }
}

class UnauthorizedException : IdentityException("Sign-in expired")

@Serializable
private data class DeviceCodeBody(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String = "",
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
)

@Serializable
private data class TokenBody(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Int = 0,
    val scope: List<String> = emptyList(),
)

@Serializable
private data class ErrorBody(
    val message: String? = null,
)

@Serializable
private data class ValidateBody(
    val login: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

@Serializable
private data class HelixUsersBody(
    val data: List<HelixUserBody> = emptyList(),
)

@Serializable
private data class HelixUserBody(
    val id: String,
    val login: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
)

@Serializable
private data class HelixStreamsBody(
    val data: List<HelixStreamBody> = emptyList(),
)

@Serializable
private data class HelixStreamBody(
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

private fun TokenBody.toGrant() =
    TokenGrant(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInSeconds = expiresIn,
        scopes = scope,
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
