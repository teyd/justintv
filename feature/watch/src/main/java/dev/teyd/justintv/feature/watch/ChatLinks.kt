package dev.teyd.justintv.feature.watch

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.core.util.PatternsCompat

// AndroidX's public web pattern handles domains, ports, paths, and query strings.
// Also retain fragments on URLs without a path, e.g. https://example.com#chat.
private val chatWebUrl = Regex("${PatternsCompat.WEB_URL.pattern()}(?:#[^\\s<>\"\\[\\]]*)?")

/** Append visible web links without changing text or making email/custom schemes clickable. */
internal fun AnnotatedString.Builder.appendChatLinks(
    text: String,
    styles: TextLinkStyles,
) {
    var cursor = 0
    for (match in chatWebUrl.findAll(text)) {
        val start = match.range.first
        val preceding = text.getOrNull(start - 1)
        // Do not link a domain embedded in an email, word, or unsupported URI scheme.
        if (preceding != null && (preceding.isLetterOrDigit() || preceding in "_@/.:+-")) continue
        if (text.getOrNull(match.range.last + 1) == '@') continue

        val visible = trimLinkPunctuation(match.value)
        if (visible.isEmpty()) continue
        val url =
            when {
                visible.startsWith("https://", ignoreCase = true) -> "https://${visible.substringAfter("://")}"
                visible.startsWith("http://", ignoreCase = true) -> "http://${visible.substringAfter("://")}"
                "://" in visible -> continue
                else -> "https://$visible"
            }
        append(text.substring(cursor, start))
        withLink(LinkAnnotation.Url(url, styles)) { append(visible) }
        cursor = start + visible.length
    }
    append(text.substring(cursor))
}

private fun trimLinkPunctuation(value: String): String {
    var end = value.length
    while (end > 0) {
        val last = value[end - 1]
        val unmatchedParenthesis = last == ')' && value.take(end).count { it == ')' } > value.take(end).count { it == '(' }
        if (last in ".,!?;:'" || unmatchedParenthesis) {
            end--
        } else {
            break
        }
    }
    return value.substring(0, end)
}
