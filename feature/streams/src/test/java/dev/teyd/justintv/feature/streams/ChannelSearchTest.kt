package dev.teyd.justintv.feature.streams

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.model.ChannelPresence
import org.junit.Test

class ChannelSearchTest {
    private val shroud =
        ChannelHit(
            id = "1",
            login = "shroud",
            displayName = "shroud",
            avatarUrl = "https://example/shroud.png",
            presence = ChannelPresence.Offline,
        )
    private val shrood =
        ChannelHit(
            id = "2",
            login = "shrood",
            displayName = "shrood",
            avatarUrl = "https://example/shrood.png",
            presence = ChannelPresence.Live("24/7 VODS", 116),
        )

    @Test
    fun `autocomplete waits for four characters`() {
        assertThat(ChannelSearch.shouldSearch("xqc")).isFalse()
        assertThat(ChannelSearch.shouldSearch("  abc")).isFalse()
        assertThat(ChannelSearch.shouldSearch("@abc")).isFalse()
        assertThat(ChannelSearch.shouldSearch("xqco")).isTrue()
        assertThat(ChannelSearch.shouldSearch("@shroud")).isTrue()
    }

    @Test
    fun `a short field can still name a channel`() {
        assertThat(ChannelSearch.typedLogin("xQc")).isEqualTo("xqc")
        assertThat(ChannelSearch.typedLogin("  @Shroud ")).isEqualTo("shroud")
        assertThat(ChannelSearch.typedLogin("   ")).isNull()
        assertThat(ChannelSearch.typedLogin("@")).isNull()
    }

    @Test
    fun `the typed channel is always first and is not repeated`() {
        val rows = ChannelSearch.rows("@Shroud", listOf(shrood, shroud))

        assertThat(rows.map { it.login }).containsExactly("shroud", "shrood").inOrder()
        assertThat(rows.first().avatarUrl).isEqualTo(shroud.avatarUrl)
    }

    @Test
    fun `a typed channel missing from the hits is still a row`() {
        val rows = ChannelSearch.rows("xQc", listOf(shrood))

        assertThat(rows).hasSize(2)
        assertThat(rows.first().login).isEqualTo("xqc")
        assertThat(rows.first().displayName).isEqualTo("xQc")
        assertThat(rows.first().presence).isEqualTo(ChannelPresence.Offline)
        assertThat(ChannelSearch.subtitle(rows.first())).isEqualTo("Channel")
    }

    @Test
    fun `an empty field has no rows`() {
        assertThat(ChannelSearch.rows("  ", listOf(shroud))).isEmpty()
    }

    @Test
    fun `live shows the title and viewers, offline shows Channel`() {
        assertThat(ChannelSearch.subtitle(shrood)).isEqualTo("24/7 VODS")
        assertThat(ChannelSearch.viewers(shrood)).isEqualTo(116)
        assertThat(ChannelSearch.subtitle(shroud)).isEqualTo("Channel")
        assertThat(ChannelSearch.viewers(shroud)).isNull()
    }
}
