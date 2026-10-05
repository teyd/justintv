package dev.teyd.justintv.feature.streams

import com.google.common.truth.Truth.assertThat
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
}
