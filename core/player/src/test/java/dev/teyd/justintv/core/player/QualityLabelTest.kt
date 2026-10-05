package dev.teyd.justintv.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QualityLabelTest {
    @Test
    fun `60fps renditions get a 60 suffix`() {
        assertThat(QualityLabel.of(1080, 60f)).isEqualTo("1080p60")
        assertThat(QualityLabel.of(720, 59.94f)).isEqualTo("720p60")
    }

    @Test
    fun `30fps renditions have no suffix`() {
        assertThat(QualityLabel.of(720, 30f)).isEqualTo("720p")
        assertThat(QualityLabel.of(360, 29.97f)).isEqualTo("360p")
    }

    @Test
    fun `an unknown frame rate is treated as standard`() {
        assertThat(QualityLabel.of(480, -1f)).isEqualTo("480p")
    }

    @Test
    fun `a rendition without a picture is audio only`() {
        assertThat(QualityLabel.of(-1, -1f)).isEqualTo("Audio only")
        assertThat(QualityLabel.of(0, 0f)).isEqualTo("Audio only")
    }
}
