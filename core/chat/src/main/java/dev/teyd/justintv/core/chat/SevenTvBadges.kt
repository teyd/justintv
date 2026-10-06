package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatBadge
import dev.teyd.justintv.core.model.ChatBadgeSource
import dev.teyd.justintv.core.network.JsonPoster
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves 7TV badges: the badge cosmetic a user has equipped.
 *
 * 7TV has no bulk users-to-badge list, so each user is looked up through the v4 GraphQL API,
 * batched through aliases and cached for the process, negatives included. A failed request
 * caches nothing, so the next message retries.
 */
class SevenTvBadges(
    private val poster: JsonPoster,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    private val badges = ConcurrentHashMap<String, ChatBadge>()
    private val none = ConcurrentHashMap.newKeySet<String>()

    /** The badge already resolved for [userId]; null when there is none or it is not known yet. */
    fun cached(userId: String): ChatBadge? = badges[userId]

    /** True once the user was looked up, whether or not a badge was found. */
    fun isKnown(userId: String): Boolean = badges.containsKey(userId) || userId in none

    /** Fetches every unknown id, caches the answers, and throws when a request fails. */
    suspend fun resolve(userIds: Collection<String>) {
        val missing = userIds.filter { it.isNotBlank() && !isKnown(it) }.distinct()
        for (batch in missing.chunked(batchSize)) {
            val resolved = SevenTvBadgeQuery.parse(poster.postJson(SevenTvBadgeQuery.URL, SevenTvBadgeQuery.query(batch)), batch.size)
            for ((index, userId) in batch.withIndex()) {
                val badge = resolved[index]
                if (badge != null) badges[userId] = badge else none += userId
            }
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}

/** The v4 GraphQL query and response shape 7TV badges come from. */
internal object SevenTvBadgeQuery {
    const val URL = "https://api.7tv.app/v4/gql"

    /** Alias `u0` answers for `userIds[0]`, and so on. */
    fun query(userIds: List<String>): String =
        buildString {
            append("query { users { ")
            userIds.forEachIndexed { index, userId ->
                append("u")
                append(index)
                append(": userByConnection(platform: TWITCH, platformId: \"")
                append(escape(userId))
                append("\") { style { activeBadge { id name description images { url mime width height scale frameCount } } } } ")
            }
            append("} }")
        }

    /** Returns one entry per alias, in query order. Throws when the body is not a valid answer. */
    fun parse(
        body: String,
        count: Int,
    ): List<ChatBadge?> {
        val root = Json.parseToJsonElement(body).jsonObject
        val data =
            root["data"] as? JsonObject
                ?: throw IllegalStateException("7TV badge query failed")
        val users = data["users"] as? JsonObject ?: JsonObject(emptyMap())
        return (0 until count).map { index ->
            // `userByConnection` and `activeBadge` are JSON null when absent, which is not an
            // error: the user simply has no equipped badge.
            val user = users["u$index"] as? JsonObject ?: return@map null
            val style = user["style"] as? JsonObject ?: return@map null
            val badge = style["activeBadge"] as? JsonObject ?: return@map null
            toBadge(badge)
        }
    }

    private fun toBadge(json: JsonObject): ChatBadge? {
        val name = json["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val description = json["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val images = json["images"]?.jsonArray ?: return null
        val url = pickImage(images) ?: return null
        return ChatBadge(
            source = ChatBadgeSource.SevenTv,
            url = url,
            title = description.ifBlank { name },
        )
    }

    /** The largest static PNG or WebP. Animated badges would cost more bytes for no gain. */
    private fun pickImage(images: JsonArray): String? {
        val candidates =
            images.mapNotNull { element ->
                val image = element as? JsonObject ?: return@mapNotNull null
                val url = image["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                BadgeImage(
                    url = url,
                    mime = image["mime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    scale = image["scale"]?.jsonPrimitive?.intOrNull ?: 1,
                    frameCount = image["frameCount"]?.jsonPrimitive?.intOrNull ?: 1,
                )
            }
        val static = candidates.filter { it.frameCount <= 1 }
        val raster = static.filter { it.mime == "image/png" || it.mime == "image/webp" }
        return (raster.ifEmpty { static }.ifEmpty { candidates }).maxByOrNull { it.scale }?.url
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private data class BadgeImage(
        val url: String,
        val mime: String,
        val scale: Int,
        val frameCount: Int,
    )
}
