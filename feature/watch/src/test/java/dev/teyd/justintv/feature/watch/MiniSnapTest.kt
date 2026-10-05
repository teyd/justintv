package dev.teyd.justintv.feature.watch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniSnapTest {

    @Test
    fun `a release in the left half snaps left`() {
        assertThat(snapMiniSide(releaseX = 100f, containerWidth = 1000f)).isEqualTo(MiniSide.Left)
    }

    @Test
    fun `a release in the right half snaps right`() {
        assertThat(snapMiniSide(releaseX = 800f, containerWidth = 1000f)).isEqualTo(MiniSide.Right)
    }

    @Test
    fun `the midpoint snaps right`() {
        assertThat(snapMiniSide(releaseX = 500f, containerWidth = 1000f)).isEqualTo(MiniSide.Right)
    }
}
