package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.network.model.PlaybackAccessToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the GraphQL request bodies for the `PlaybackAccessToken` operation.
 *
 * The persisted query (a hash) is tried first because it is small. Twitch occasionally
 * retires a persisted hash, so the full query is available as a fallback.
 */
object GqlRequestBuilder {
    /** `PlaybackAccessToken` persisted query hash, used by the web player. */
    const val ACCESS_TOKEN_QUERY_HASH = "0828119ded1c13477966434e15800ff57ddacf13ba1911c129dc2200705b0712"

    val ACCESS_TOKEN_QUERY: String =
        """
        query PlaybackAccessToken(${'$'}login: String!, ${'$'}isLive: Boolean!, ${'$'}vodID: ID!, ${'$'}isVod: Boolean!, ${'$'}playerType: String!) {
          streamPlaybackAccessToken(channelName: ${'$'}login, params: {platform: "web", playerBackend: "mediaplayer", playerType: ${'$'}playerType}) @include(if: ${'$'}isLive) {
            value
            signature
            __typename
          }
        }
        """.trimIndent()

    fun persistedAccessTokenRequest(
        login: String,
        playerType: String,
    ): String =
        buildJsonObject {
            put("operationName", "PlaybackAccessToken")
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", ACCESS_TOKEN_QUERY_HASH)
                }
            }
            putJsonObject("variables") {
                accessTokenVariables(this::put, login, playerType)
            }
        }.toString()

    fun fullAccessTokenRequest(
        login: String,
        playerType: String,
    ): String =
        buildJsonObject {
            put("operationName", "PlaybackAccessToken")
            put("query", ACCESS_TOKEN_QUERY)
            putJsonObject("variables") {
                accessTokenVariables(this::put, login, playerType)
            }
        }.toString()

    private fun accessTokenVariables(
        put: (String, String) -> Unit,
        login: String,
        playerType: String,
    ) {
        put("isLive", "true")
        put("login", login.lowercase())
        put("isVod", "false")
        put("vodID", "")
        put("playerType", playerType)
    }
}

/**
 * Extracts the stream playback access token from a GraphQL response.
 *
 * Returns a [ParseResult.Failure] with a human readable reason for the cases the app cares
 * about: an error payload, a missing token (offline channel), and malformed JSON.
 */
object PlaybackTokenParser {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface ParseResult {
        data class Success(
            val token: PlaybackAccessToken,
        ) : ParseResult

        data class Failure(
            val reason: FailureReason,
            val message: String,
        ) : ParseResult
    }

    enum class FailureReason { OfflineOrUnknownChannel, GraphQlError, Malformed }

    fun parse(body: String): ParseResult =
        try {
            val root = json.parseToJsonElement(body).jsonObject
            val errors = root["errors"]
            if (errors != null) {
                ParseResult.Failure(
                    FailureReason.GraphQlError,
                    errors.toString().take(200),
                )
            } else {
                val token =
                    root["data"]
                        ?.jsonObject
                        ?.get("streamPlaybackAccessToken")
                        ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                        ?.jsonObject
                        ?.let { streamToken(it) }
                if (token == null) {
                    ParseResult.Failure(
                        FailureReason.OfflineOrUnknownChannel,
                        "streamPlaybackAccessToken is null",
                    )
                } else {
                    ParseResult.Success(token)
                }
            }
        } catch (e: Exception) {
            ParseResult.Failure(FailureReason.Malformed, e.message ?: "malformed response")
        }

    private fun streamToken(node: JsonObject): PlaybackAccessToken? {
        val value = node["value"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
        val signature = node["signature"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
        return PlaybackAccessToken(value = value, signature = signature)
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        try {
            content.takeIf { isString.not() || it.isNotEmpty() }
        } catch (_: IllegalArgumentException) {
            null
        }
}
