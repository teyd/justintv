package dev.teyd.justintv.core.model

/** A piece of a chat message: plain text or an emote image. */
sealed interface ChatSegment {
    data class Text(
        val text: String,
    ) : ChatSegment

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

/** Where a chat badge comes from. Declaration order is the order chat draws them in. */
enum class ChatBadgeSource { Twitch, Chatterino, Ffz, Bttv, SevenTv }

/** One badge drawn in front of a chatter's name. */
data class ChatBadge(
    val source: ChatBadgeSource,
    val url: String,
    /** Read by screen readers and shown as a tooltip by chat clients that have one. */
    val title: String,
)

/** One chat line. Only what the UI draws: who said it and what, with emotes resolved. */
data class ChatMessage(
    val id: String,
    val user: String,
    /**
     * Name colour as `#RRGGBB`. Twitch sends one when the viewer set it; otherwise the client
     * hashes the nick into a stable palette so names still read as distinct.
     */
    val color: String,
    val segments: List<ChatSegment>,
    val isAction: Boolean = false,
    /**
     * When Twitch says the message was sent, as Unix epoch milliseconds from the
     * `tmi-sent-ts` tag. Null when the source did not carry it; the UI then shows no time.
     */
    val timestampMs: Long? = null,
    val badges: List<ChatBadge> = emptyList(),
)
