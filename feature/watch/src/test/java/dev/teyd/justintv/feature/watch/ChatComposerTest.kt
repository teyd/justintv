package dev.teyd.justintv.feature.watch

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.chat.Emote
import dev.teyd.justintv.core.chat.EmoteSource
import org.junit.Test

class ChatComposerTest {
    @Test
    fun `an emote is inserted as its own word`() {
        val (text, cursor) = insertEmote("hello", cursor = 5, name = "KEKW")
        assertThat(text).isEqualTo("hello KEKW ")
        assertThat(cursor).isEqualTo(text.length)
    }

    @Test
    fun `an emote at the start does not get a leading space`() {
        val (text, _) = insertEmote("", cursor = 0, name = "KEKW")
        assertThat(text).isEqualTo("KEKW ")
    }

    @Test
    fun `search filters by name and source`() {
        val emotes =
            listOf(
                Emote("KEKW", "https://example/k.webp", source = EmoteSource.SevenTv, stillUrl = "https://example/k.png"),
                Emote("catJAM", "https://example/c.webp", source = EmoteSource.Bttv),
            )
        assertThat(filterEmotes(emotes, EmoteSource.Bttv, "jam").map { it.name })
            .containsExactly("catJAM")
    }

    @Test
    fun `a still frame is kept beside the animated file`() {
        val emote =
            Emote(
                name = "SourPls",
                url = "https://cdn.betterttv.net/emote/1/2x.png",
                source = EmoteSource.Bttv,
                stillUrl = "https://cdn.betterttv.net/emote/1/2x.png",
            )
        assertThat(emote.stillUrl).endsWith(".png")
    }
}
