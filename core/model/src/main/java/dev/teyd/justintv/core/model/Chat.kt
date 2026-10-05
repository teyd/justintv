package dev.teyd.justintv.core.model

/** A piece of a chat message: plain text or an emote image. */
sealed interface ChatSegment {
    data class Text(val text: String) : ChatSegment

    /**
     * An emote.
     *
     * @param aspectRatio width divided by height, so wide emotes are not squashed.
     */
    data class Emote(
        val name: String,
        val url: String,
        val aspectRatio: Float = 1f,
    ) : ChatSegment
}

/** One chat line. Only what the UI draws: who said it and what, with emotes resolved. */
data class ChatMessage(
    val id: String,
    val user: String,
    val segments: List<ChatSegment>,
    val isAction: Boolean = false,
)
