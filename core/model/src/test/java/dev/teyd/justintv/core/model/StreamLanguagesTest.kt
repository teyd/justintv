package dev.teyd.justintv.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamLanguagesTest {

    @Test
    fun `codes are unique`() {
        val codes = StreamLanguages.ALL.map { it.code }

        assertThat(codes).containsNoDuplicates()
    }

    @Test
    fun `sanitize keeps only known codes in list order`() {
        assertThat(StreamLanguages.sanitize(setOf("DE", "nope", "EN"))).containsExactly("EN", "DE").inOrder()
    }

    @Test
    fun `sanitize is case sensitive because the api enum is`() {
        assertThat(StreamLanguages.sanitize(setOf("en"))).isEmpty()
    }

    @Test
    fun `labels resolve and unknown codes do not`() {
        assertThat(StreamLanguages.label("DE")).isEqualTo("Deutsch")
        assertThat(StreamLanguages.label("XX")).isNull()
        assertThat(StreamLanguages.label(null)).isNull()
    }
}
