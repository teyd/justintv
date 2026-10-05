package dev.teyd.justintv.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class FormatTest {

    @Test
    fun `small counts are shown as is`() {
        assertThat(formatViewers(0)).isEqualTo("0")
        assertThat(formatViewers(999)).isEqualTo("999")
    }

    @Test
    fun `thousands are compacted with one decimal under ten thousand`() {
        assertThat(formatViewers(1_000)).isEqualTo("1K")
        assertThat(formatViewers(1_234)).isEqualTo("1.2K")
        assertThat(formatViewers(9_950)).isEqualTo("10K")
    }

    @Test
    fun `larger thousands drop the decimal`() {
        assertThat(formatViewers(15_000)).isEqualTo("15K")
        assertThat(formatViewers(162_687)).isEqualTo("163K")
    }

    @Test
    fun `millions are compacted`() {
        assertThat(formatViewers(1_200_000)).isEqualTo("1.2M")
        assertThat(formatViewers(12_000_000)).isEqualTo("12M")
    }

    private val now = Instant.parse("2026-10-05T03:33:00Z")

    @Test
    fun `uptime shows hours and minutes`() {
        assertThat(formatUptime("2026-10-05T01:00:00Z", now)).isEqualTo("2h 33min")
    }

    @Test
    fun `uptime under an hour is just minutes`() {
        assertThat(formatUptime("2026-10-05T03:00:00Z", now)).isEqualTo("33min")
        assertThat(formatUptime("2026-10-05T03:33:00Z", now)).isEqualTo("0min")
    }

    @Test
    fun `a whole number of hours has no minutes part`() {
        assertThat(formatUptime("2026-10-05T01:33:00Z", now)).isEqualTo("2h")
    }

    @Test
    fun `a stream that has run for days shows days and hours`() {
        assertThat(formatUptime("2026-10-03T23:33:00Z", now)).isEqualTo("1d 4h")
    }

    @Test
    fun `missing or unreadable start times give nothing`() {
        assertThat(formatUptime(null, now)).isNull()
        assertThat(formatUptime("not a date", now)).isNull()
    }
}
