package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatNameColorTest {

    @Test
    fun `a tagged colour is used as is`() {
        assertThat(ChatNameColor.resolve("#a1B2c3", "anyone")).isEqualTo("#A1B2C3")
    }

    @Test
    fun `blank and garbage tags fall back to the palette`() {
        assertThat(ChatNameColor.resolve("", "Viewer")).isIn(ChatNameColor.DEFAULT_PALETTE)
        assertThat(ChatNameColor.resolve(null, "Viewer")).isIn(ChatNameColor.DEFAULT_PALETTE)
        assertThat(ChatNameColor.resolve("red", "Viewer")).isIn(ChatNameColor.DEFAULT_PALETTE)
        assertThat(ChatNameColor.resolve("#GG0000", "Viewer")).isIn(ChatNameColor.DEFAULT_PALETTE)
    }

    @Test
    fun `the same nick always gets the same colour`() {
        val first = ChatNameColor.resolve(null, "Caedrel")
        val second = ChatNameColor.resolve("", "caedrel")

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `different nicks spread across the palette`() {
        val colours = listOf("alice", "bob", "carol", "dave", "erin", "frank", "grace", "heidi")
            .map { ChatNameColor.resolve(null, it) }
            .toSet()

        assertThat(colours.size).isAtLeast(4)
    }

    @Test
    fun `hex validation accepts only seven character colours`() {
        assertThat(ChatNameColor.isHexColor("#FFFFFF")).isTrue()
        assertThat(ChatNameColor.isHexColor("#fff")).isFalse()
        assertThat(ChatNameColor.isHexColor("FFFFFF")).isFalse()
    }
}
