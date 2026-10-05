package dev.teyd.justintv.core.chat

/** A parsed IRC line. Twitch chat is IRCv3 with message tags. */
data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String?,
    val command: String,
    val params: List<String>,
    val trailing: String?,
) {
    /** The nick before the `!` in a `nick!user@host` prefix. */
    val nick: String? get() = prefix?.substringBefore('!')?.takeIf { it.isNotEmpty() }
}

object IrcParser {

    /**
     * Parses one IRC line (without its CRLF), or returns null if it is empty or malformed.
     *
     * Shape: `[@tags ][:prefix ]COMMAND [params] [:trailing]`.
     */
    fun parse(line: String): IrcMessage? {
        var rest = line.trimEnd('\r', '\n')
        if (rest.isBlank()) return null

        var tags: Map<String, String> = emptyMap()
        if (rest.startsWith("@")) {
            val end = rest.indexOf(' ')
            if (end == -1) return null
            tags = parseTags(rest.substring(1, end))
            rest = rest.substring(end + 1).trimStart()
        }

        var prefix: String? = null
        if (rest.startsWith(":")) {
            val end = rest.indexOf(' ')
            if (end == -1) return null
            prefix = rest.substring(1, end)
            rest = rest.substring(end + 1).trimStart()
        }

        var trailing: String? = null
        val trailingStart = rest.indexOf(" :")
        if (trailingStart != -1) {
            trailing = rest.substring(trailingStart + 2)
            rest = rest.substring(0, trailingStart)
        }

        val parts = rest.split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return IrcMessage(
            tags = tags,
            prefix = prefix,
            command = parts.first(),
            params = parts.drop(1),
            trailing = trailing,
        )
    }

    private fun parseTags(raw: String): Map<String, String> {
        val tags = LinkedHashMap<String, String>()
        for (pair in raw.split(';')) {
            if (pair.isEmpty()) continue
            val equals = pair.indexOf('=')
            if (equals == -1) {
                tags[pair] = ""
            } else {
                tags[pair.substring(0, equals)] = unescape(pair.substring(equals + 1))
            }
        }
        return tags
    }

    /** IRCv3 tag value escapes: `\:` `;`, `\s` space, `\\` backslash, `\r` and `\n`. */
    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    ':' -> out.append(';')
                    's' -> out.append(' ')
                    '\\' -> out.append('\\')
                    'r' -> out.append('\r')
                    'n' -> out.append('\n')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
