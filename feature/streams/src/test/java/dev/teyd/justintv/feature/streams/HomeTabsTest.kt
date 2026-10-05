package dev.teyd.justintv.feature.streams

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeTabsTest {
    @Test
    fun `logged out viewers do not get a following tab`() {
        assertThat(homeTabs(isLoggedIn = false)).containsExactly(HomeTab.Live, HomeTab.Categories).inOrder()
    }

    @Test
    fun `logged in viewers get following first`() {
        assertThat(homeTabs(isLoggedIn = true))
            .containsExactly(HomeTab.Following, HomeTab.Live, HomeTab.Categories)
            .inOrder()
    }
}

class LanguageFilterLabelTest {
    @Test
    fun `an empty selection is not a count`() {
        assertThat(languageFilterLabel(emptySet())).isEqualTo("Languages")
    }

    @Test
    fun `one language uses its name, several use a count`() {
        assertThat(languageFilterLabel(setOf("DE"))).isEqualTo("Deutsch")
        assertThat(languageFilterLabel(setOf("EN", "DE"))).isEqualTo("2 languages")
    }
}
