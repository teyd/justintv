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
