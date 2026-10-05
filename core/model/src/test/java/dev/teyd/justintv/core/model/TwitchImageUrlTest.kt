package dev.teyd.justintv.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TwitchImageUrlTest {

    @Test
    fun `the preview height placeholder is filled in`() {
        assertThat(twitchImageUrl("https://static-cdn.jtvnw.net/previews-ttv/live_user_x-440x{height}.jpg"))
            .isEqualTo("https://static-cdn.jtvnw.net/previews-ttv/live_user_x-440x248.jpg")
    }

    @Test
    fun `both placeholders are filled in`() {
        assertThat(twitchImageUrl("https://example/{width}x{height}.jpg", width = 100, height = 50))
            .isEqualTo("https://example/100x50.jpg")
    }

    @Test
    fun `a null template stays null`() {
        assertThat(twitchImageUrl(null)).isNull()
    }
}
