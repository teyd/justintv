package dev.teyd.justintv.feature.watch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.data.ChatTextSize
import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.model.ChatSegment
import org.junit.Test

class ChatLineStyleTest {
    private val styles = TextLinkStyles(style = SpanStyle(color = Color.Blue))

    private val message =
        ChatMessage(
            id = "message",
            user = "alice",
            color = "#123456",
            segments =
                listOf(
                    ChatSegment.Text("hello"),
                    ChatSegment.Emote("Kappa", "https://cdn.example.com/kappa.png"),
                ),
        )

    @Test
    fun `names use their Twitch color when colors are on`() {
        val name = buildChatText(message, styles).text.spanStyles.first()
        assertThat(name.start).isEqualTo(0)
        assertThat(name.end).isEqualTo("alice".length)
        assertThat(name.item.color).isEqualTo(parseChatColor("#123456"))
    }

    @Test
    fun `names inherit the text color when colors are off`() {
        val name = buildChatText(message, styles, nameColor = Color.Unspecified).text.spanStyles.first()
        assertThat(name.item.color).isEqualTo(Color.Unspecified)
    }

    @Test
    fun `emotes scale with the chat text size`() {
        val small = buildChatText(message, styles, textSize = ChatTextSize.Small)
        val large = buildChatText(message, styles, textSize = ChatTextSize.Large)
        assertThat(
            small.inline
                .getValue("e1")
                .placeholder.height,
        ).isEqualTo(ChatTextSize.Small.emoteSp.sp)
        assertThat(
            large.inline
                .getValue("e1")
                .placeholder.height,
        ).isEqualTo(ChatTextSize.Large.emoteSp.sp)
    }
}
