package dev.teyd.justintv.core.model

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
)
