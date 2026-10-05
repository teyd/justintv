package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.model.ChatSegment

/** A Twitch native emote occurrence: its id and the code point range it covers (inclusive). */
data class TwitchEmoteRange(val id: String, val start: Int, val end: Int)

/**
 * Turns IRC messages into [ChatMessage]s with emotes resolved.
 *
 * Twitch native emotes come from the `emotes` tag as code point ranges. Third-party emotes
 * (7TV, BTTV, FFZ) are matched by whole-word name in whatever text is left.
 */
object ChatMessageParser {

    private const val ACTION_PREFIX = "\u0001ACTION "
    private const val ACTION_SUFFIX = "\u0001"

    /** Returns null for anything that is not a chat line. */
    fun parse(irc: IrcMessage, index: EmoteIndex): ChatMessage? {
        if (irc.command != "PRIVMSG") return null
        var text = irc.trailing ?: return null

        var isAction = false
        if (text.startsWith(ACTION_PREFIX) && text.endsWith(ACTION_SUFFIX)) {
            text = text.removePrefix(ACTION_PREFIX).removeSuffix(ACTION_SUFFIX)
            isAction = true
        }

        val user = irc.tags["display-name"]?.takeIf { it.isNotBlank() } ?: irc.nick ?: return null
        val ranges = parseEmoteTag(irc.tags["emotes"])
        val color = ChatNameColor.resolve(irc.tags["color"], user)

        return ChatMessage(
            id = irc.tags["id"] ?: "${user}-${text.hashCode()}-${System.nanoTime()}",
            user = user,
            color = color,
            segments = tokenize(text, ranges, index),
            isAction = isAction,
        )
    }

    /** `25:0-4,12-16/1902:6-10` becomes three ranges, sorted by position. */
    fun parseEmoteTag(tag: String?): List<TwitchEmoteRange> {
        if (tag.isNullOrBlank()) return emptyList()
        val ranges = mutableListOf<TwitchEmoteRange>()
        for (entry in tag.split('/')) {
            val id = entry.substringBefore(':', "")
            val positions = entry.substringAfter(':', "")
            if (id.isEmpty() || positions.isEmpty()) continue
            for (position in positions.split(',')) {
                val start = position.substringBefore('-').toIntOrNull() ?: continue
                val end = position.substringAfter('-', "").toIntOrNull() ?: continue
                if (start in 0..end) ranges += TwitchEmoteRange(id, start, end)
            }
        }
        return ranges.sortedBy { it.start }
    }

    fun tokenize(
        text: String,
        twitchEmotes: List<TwitchEmoteRange>,
        index: EmoteIndex,
    ): List<ChatSegment> {
        val codePoints = text.codePoints().toArray()
        val segments = ArrayList<ChatSegment>()
        var position = 0

        fun slice(from: Int, toExclusive: Int): String =
            String(codePoints, from, (toExclusive - from).coerceAtLeast(0))

        for (range in twitchEmotes) {
            // Ranges that overlap an earlier one or run past the text are ignored, not trusted.
            if (range.start < position || range.start >= codePoints.size) continue
            val end = minOf(range.end, codePoints.size - 1)

            if (range.start > position) {
                appendThirdParty(segments, slice(position, range.start), index)
            }
            segments += ChatSegment.Emote(
                name = slice(range.start, end + 1),
                url = twitchEmoteUrl(range.id),
            )
            position = end + 1
        }
        if (position < codePoints.size) {
            appendThirdParty(segments, slice(position, codePoints.size), index)
        }
        return mergeText(segments)
    }

    fun twitchEmoteUrl(id: String): String =
        "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0"

    private fun appendThirdParty(out: MutableList<ChatSegment>, text: String, index: EmoteIndex) {
        if (text.isEmpty()) return
        if (index.size == 0) {
            out += ChatSegment.Text(text)
            return
        }
        val word = StringBuilder()
        val buffer = StringBuilder()

        fun flushWord() {
            if (word.isEmpty()) return
            val emote = index[word.toString()]
            if (emote != null) {
                if (buffer.isNotEmpty()) {
                    out += ChatSegment.Text(buffer.toString())
                    buffer.setLength(0)
                }
                out += ChatSegment.Emote(emote.name, emote.url, emote.aspectRatio)
            } else {
                buffer.append(word)
            }
            word.setLength(0)
        }

        for (char in text) {
            if (char.isWhitespace()) {
                flushWord()
                buffer.append(char)
            } else {
                word.append(char)
            }
        }
        flushWord()
        if (buffer.isNotEmpty()) out += ChatSegment.Text(buffer.toString())
    }

    private fun mergeText(segments: List<ChatSegment>): List<ChatSegment> {
        val merged = ArrayList<ChatSegment>(segments.size)
        for (segment in segments) {
            val last = merged.lastOrNull()
            if (segment is ChatSegment.Text && last is ChatSegment.Text) {
                merged[merged.lastIndex] = ChatSegment.Text(last.text + segment.text)
            } else {
                merged += segment
            }
        }
        return merged
    }
}
