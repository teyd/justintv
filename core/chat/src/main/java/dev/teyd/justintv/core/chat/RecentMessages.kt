package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.network.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The last messages in a channel, from https://recent-messages.robotty.de .
 *
 * Twitch IRC only delivers lines sent after you join. This fills that gap. A failure here is
 * not fatal: chat still works, it just starts empty.
 */
class RecentMessages(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://recent-messages.robotty.de/api/v2/recent-messages",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(
        login: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<IrcMessage> {
        val url = "$baseUrl/${login.lowercase()}?limit=$limit&hide_moderation_messages=true&hide_moderated_messages=true"
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "JustinTV")
                .build()
        val body =
            try {
                client.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) return emptyList()
                    withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
                }
            } catch (_: IOException) {
                return emptyList()
            }
        val parsed =
            try {
                json.decodeFromString<RecentMessagesResponse>(body)
            } catch (_: Exception) {
                return emptyList()
            }
        return parsed.messages.mapNotNull { raw ->
            IrcParser.parse(IrcLineNormalizer.normalize(raw))
        }
    }

    @Serializable
    private data class RecentMessagesResponse(
        val messages: List<String> = emptyList(),
    )

    companion object {
        const val DEFAULT_LIMIT = 80
    }
}
