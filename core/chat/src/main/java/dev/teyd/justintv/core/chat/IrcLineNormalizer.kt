package dev.teyd.justintv.core.chat

/**
 * Recent-messages sometimes omits the space-colon before a PRIVMSG body.
 * The IRC parser needs that colon, so it is inserted when it is missing.
 *
 * A colon earlier in the line (the sender prefix) does not count.
 */
object IrcLineNormalizer {
    fun normalize(line: String): String {
        val marker = " PRIVMSG "
        val at = line.indexOf(marker)
        if (at == -1) return line
        val after = line.substring(at + marker.length)
        if (after.contains(" :")) return line
        val channelEnd = after.indexOf(' ')
        if (channelEnd == -1) return line
        val insertAt = at + marker.length + channelEnd
        return line.substring(0, insertAt) + " :" + line.substring(insertAt + 1)
    }
}
