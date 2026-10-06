package dev.teyd.justintv.feature.watch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.model.ChatSegment
import org.junit.Test

class ChatLinksTest {
    private val styles = TextLinkStyles(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline))

    private fun linked(text: String): AnnotatedString = buildAnnotatedString { appendChatLinks(text, styles) }

    private fun urls(text: AnnotatedString): List<String> =
        text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }

    @Test
    fun `web links are styled and preserve the displayed message`() {
        val original = "watch https://example.com/live?q=chat&lang=en#here now"
        val text = linked(original)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertThat(text.text).isEqualTo(original)
        assertThat(urls(text)).containsExactly("https://example.com/live?q=chat&lang=en#here")
        assertThat(text.text.substring(link.start, link.end)).isEqualTo("https://example.com/live?q=chat&lang=en#here")
        assertThat((link.item as LinkAnnotation.Url).styles).isEqualTo(styles)
    }

    @Test
    fun `multiple links and bare domains use independent destinations`() {
        val text = linked("http://example.com www.twitch.tv/channel twitch.tv/other")
        assertThat(urls(text)).containsExactly("http://example.com", "https://www.twitch.tv/channel", "https://twitch.tv/other").inOrder()
    }

    @Test
    fun `uppercase schemes are normalized without changing visible text`() {
        val text = linked("HTTPS://Example.COM/channel")
        assertThat(text.text).isEqualTo("HTTPS://Example.COM/channel")
        assertThat(urls(text)).containsExactly("https://Example.COM/channel")
    }

    @Test
    fun `surrounding punctuation is not part of the clickable range`() {
        val original = "See (https://example.com/live), then https://twitch.tv/test!"
        val text = linked(original)
        assertThat(text.text).isEqualTo(original)
        assertThat(urls(text)).containsExactly("https://example.com/live", "https://twitch.tv/test").inOrder()
    }

    @Test
    fun `balanced parentheses in paths remain part of the link`() {
        assertThat(urls(linked("(https://en.wikipedia.org/wiki/Twitch_(service))")))
            .containsExactly("https://en.wikipedia.org/wiki/Twitch_(service)")
    }

    @Test
    fun `fragments ports and encoded paths are retained`() {
        assertThat(urls(linked("https://example.com#chat https://example.com:8443/a%20b?q=one%26two")))
            .containsExactly("https://example.com#chat", "https://example.com:8443/a%20b?q=one%26two")
            .inOrder()
    }

    @Test
    fun `emails and custom schemes do not become web links`() {
        val original =
            "user@example.com user.name@example.com ftp://example.com file://example.com " +
                "intent://example.com rtsp://example.com javascript:example.com"
        val text = linked(original)
        assertThat(text.text).isEqualTo(original)
        assertThat(urls(text)).isEmpty()
    }

    @Test
    fun `plain and empty messages are unchanged`() {
        for (original in listOf("", "hello chat", "https://", "not a link")) {
            val text = linked(original)
            assertThat(text.text).isEqualTo(original)
            assertThat(urls(text)).isEmpty()
        }
    }

    @Test
    fun `link ranges use UTF16 offsets after emoji`() {
        val original = "🎉 https://example.com 🎉"
        val text = linked(original)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertThat(link.start).isEqualTo(original.indexOf("https://"))
        assertThat(original.substring(link.start, link.end)).isEqualTo("https://example.com")
    }

    @Test
    fun `link text stays correctly positioned around inline emotes`() {
        val message =
            ChatMessage(
                id = "message",
                user = "alice",
                color = "#123456",
                segments =
                    listOf(
                        ChatSegment.Text("https://example.com "),
                        ChatSegment.Emote("Kappa", "https://cdn.example.com/kappa.png"),
                        ChatSegment.Text(" twitch.tv/channel"),
                    ),
            )
        val built = buildChatText(message, styles)
        assertThat(built.text.text).isEqualTo("alice: https://example.com Kappa twitch.tv/channel")
        assertThat(built.inline.keys).containsExactly("e1")
        assertThat(urls(built.text)).containsExactly("https://example.com", "https://twitch.tv/channel").inOrder()
        val links = built.text.getLinkAnnotations(0, built.text.length)
        assertThat(
            links.map { built.text.text.substring(it.start, it.end) },
        ).containsExactly("https://example.com", "twitch.tv/channel").inOrder()
    }

    @Test
    fun `usernames are not linkified in action messages`() {
        val built =
            buildChatText(
                ChatMessage("action", "example.com", "#123456", listOf(ChatSegment.Text("visits twitch.tv/channel")), isAction = true),
                styles,
            )
        assertThat(built.text.text).isEqualTo("example.com visits twitch.tv/channel")
        assertThat(urls(built.text)).containsExactly("https://twitch.tv/channel")
    }

    @Test
    fun `a shown timestamp is prepended before the name`() {
        val built =
            buildChatText(
                ChatMessage("message", "alice", "#123456", listOf(ChatSegment.Text("hi")), timestampMs = 1_700_000_000_000),
                styles,
                timestamp = "22:13",
                timestampColor = Color.Gray,
            )

        assertThat(built.text.text).isEqualTo("22:13 alice: hi")
    }

    @Test
    fun `no timestamp leaves the line unchanged`() {
        val built =
            buildChatText(
                ChatMessage("message", "alice", "#123456", listOf(ChatSegment.Text("hi")), timestampMs = 1_700_000_000_000),
                styles,
            )

        assertThat(built.text.text).isEqualTo("alice: hi")
    }
}
