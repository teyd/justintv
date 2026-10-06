package dev.teyd.justintv.feature.watch

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class ChatTimeTest {
    @Test
    fun `renders the clock time in the given zone`() {
        // 2023-11-14T22:13:20Z.
        assertThat(formatChatTime(1_700_000_000_000, ZoneId.of("UTC"))).isEqualTo("22:13")
        assertThat(formatChatTime(1_700_000_000_000, ZoneId.of("America/New_York"))).isEqualTo("17:13")
    }

    @Test
    fun `two digit zero padding keeps times aligned`() {
        assertThat(formatChatTime(0, ZoneId.of("UTC"))).isEqualTo("00:00")
    }

    @Test
    fun `twelve hour format uses AM and PM`() {
        assertThat(formatChatTime(1_700_000_000_000, ZoneId.of("UTC"), is24Hour = false)).isEqualTo("10:13 PM")
        assertThat(formatChatTime(0, ZoneId.of("UTC"), is24Hour = false)).isEqualTo("12:00 AM")
    }
}
