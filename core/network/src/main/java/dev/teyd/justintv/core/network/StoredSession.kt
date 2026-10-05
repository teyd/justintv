package dev.teyd.justintv.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tokens and the account they belong to. The vault encrypts this; callers never see the file. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val userId: String,
    val login: String,
    val displayName: String,
    val scopes: List<String> = emptyList(),
)

interface TokenVault {
    suspend fun load(): StoredSession?

    suspend fun save(session: StoredSession)

    suspend fun clear()
}

object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(session: StoredSession): String = json.encodeToString(session)

    fun decode(text: String): StoredSession? =
        try {
            json.decodeFromString<StoredSession>(text)
        } catch (_: Exception) {
            null
        }
}
