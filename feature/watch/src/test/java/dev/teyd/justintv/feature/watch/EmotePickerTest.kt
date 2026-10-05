package dev.teyd.justintv.feature.watch

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.chat.Emote
import dev.teyd.justintv.core.chat.EmoteSource
import org.junit.Test

class EmotePickerTest {

    private fun emote(name: String, source: EmoteSource) = Emote(name, "https://example/$name", source = source)

    private val emotes = listOf(
        emote("catJAM", EmoteSource.Bttv),
        emote("KEKW", EmoteSource.SevenTv),
        emote("ZrehplaR", EmoteSource.Ffz),
        emote("Clap", EmoteSource.SevenTv),
    )

    @Test
    fun `sections follow display order and sort by name`() {
        val sections = sectionsOf(emotes)
        assertThat(sections.map { it.source })
            .containsExactly(EmoteSource.SevenTv, EmoteSource.Bttv, EmoteSource.Ffz).inOrder()
        assertThat(sections.first().emotes.map { it.name }).containsExactly("Clap", "KEKW").inOrder()
    }

    @Test
    fun `a provider with no emotes has no section`() {
        val sections = sectionsOf(emotes.filter { it.source != EmoteSource.Bttv })
        assertThat(sections.map { it.source }).containsExactly(EmoteSource.SevenTv, EmoteSource.Ffz).inOrder()
    }

    @Test
    fun `headers sit before each section's emotes`() {
        // 7TV: header 0, two emotes. BTTV: header 3, one emote. FFZ: header 5.
        assertThat(headerIndices(sectionsOf(emotes))).containsExactly(0, 3, 5).inOrder()
    }

    @Test
    fun `prefetch runs in the order the grid lists emotes`() {
        // The viewer scrolls top to bottom, so the first screens must be warm first.
        assertThat(pickerOrder(emotes).map { it.name })
            .containsExactly("Clap", "KEKW", "catJAM", "ZrehplaR").inOrder()
    }

    @Test
    fun `the very top of the all grid is the all tab`() {
        val headers = headerIndices(sectionsOf(emotes))
        assertThat(sectionAtTop(headers, firstVisibleIndex = 0, firstVisibleOffset = 0)).isNull()
    }

    @Test
    fun `scrolling into a section selects it`() {
        val headers = headerIndices(sectionsOf(emotes))
        assertThat(sectionAtTop(headers, firstVisibleIndex = 0, firstVisibleOffset = 12)).isEqualTo(0)
        assertThat(sectionAtTop(headers, firstVisibleIndex = 4, firstVisibleOffset = 0)).isEqualTo(1)
        assertThat(sectionAtTop(headers, firstVisibleIndex = 5, firstVisibleOffset = 0)).isEqualTo(2)
    }
}
