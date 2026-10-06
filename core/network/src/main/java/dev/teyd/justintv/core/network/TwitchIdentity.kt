package dev.teyd.justintv.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

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
 * Twitch OAuth: device-code sign-in, token refresh, and token validation.
 *
 * Data calls made with the resulting token live on [HelixClient]. Playback tokens stay on
 * [GqlClient], anonymous, and never see what this class sends.
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
        val parsed = twitchJson.decodeFromString<DeviceCodeBody>(response.body)
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
            if (isRejectedRefresh(response.code, response.body)) throw UnauthorizedException()
            throw IdentityException(errorMessage(response.body) ?: "Could not refresh the sign-in")
        }
        return twitchJson.decodeFromString<TokenBody>(response.body).toGrant()
    }

    /** Null when the token is rejected. Other failures throw. */
    suspend fun validate(accessToken: String): TwitchUser? {
        val response = get(VALIDATE_URL, bearer = accessToken, clientId = null, oauthPrefix = "OAuth")
        if (response.code == 401) return null
        if (response.code != 200) {
            throw IdentityException(errorMessage(response.body) ?: "Could not check the sign-in")
        }
        val parsed = twitchJson.decodeFromString<ValidateBody>(response.body)
        val id = parsed.userId ?: return null
        val login = parsed.login ?: return null
        return TwitchUser(id = id, login = login, displayName = parsed.login)
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
        private const val ACTIVATE_URL = "https://www.twitch.tv/activate"
        private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

        /**
         * True only when Twitch says the refresh token itself is dead. A missing client id, a
         * 5xx, or a transport error is not this: those must not sign the user out.
         */
        fun isRejectedRefresh(
            code: Int,
            body: String,
        ): Boolean {
            if (code == 401) return true
            if (code != 400) return false
            val text = body.lowercase()
            return "invalid refresh token" in text || "invalid_grant" in text
        }

        fun parseDevicePoll(
            code: Int,
            body: String,
        ): DevicePoll {
            if (code == 200) {
                return DevicePoll.Granted(twitchJson.decodeFromString<TokenBody>(body).toGrant())
            }
            val message = errorMessage(body).orEmpty()
            return when {
                message.contains("authorization_pending", ignoreCase = true) -> DevicePoll.Pending
                message.contains("slow_down", ignoreCase = true) -> DevicePoll.SlowDown
                else -> DevicePoll.Rejected(message.ifBlank { "Sign-in was rejected" })
            }
        }
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

internal val twitchJson = Json { ignoreUnknownKeys = true }

internal fun errorMessage(body: String): String? =
    try {
        twitchJson.decodeFromString<ErrorBody>(body).message?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

@Serializable
private data class ValidateBody(
    val login: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

private fun TokenBody.toGrant() =
    TokenGrant(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInSeconds = expiresIn,
        scopes = scope,
    )
