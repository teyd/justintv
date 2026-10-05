package dev.teyd.justintv.core.model

/**
 * Twitch serves thumbnails from templates like `...-440x{height}.jpg`. Small images are the
 * default: the grids and the mini dock draw at most a couple of hundred pixels wide.
 */
fun twitchImageUrl(template: String?, width: Int = 440, height: Int = 248): String? =
    template
        ?.replace("{width}", width.toString())
        ?.replace("{height}", height.toString())

/** A category on Twitch, such as "Just Chatting". Loaded from the API, never hardcoded. */
data class Game(
    val id: String,
    val name: String,
    val displayName: String,
    val boxArtUrl: String?,
    val viewerCount: Int,
)

/** A channel that is live right now. */
data class LiveStream(
    val id: String,
    val login: String,
    val displayName: String,
    val title: String,
    val viewerCount: Int,
    val previewUrl: String?,
    val avatarUrl: String?,
    val gameName: String?,
    val language: String?,
    /** ISO-8601 start time, when the directory call included one. */
    val startedAt: String? = null,
)
