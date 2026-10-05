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

    @Test
    fun `a fast fling left wins over a release in the right half`() {
        assertThat(
            snapMiniSide(releaseX = 800f, containerWidth = 1000f, velocityX = -MINI_FLING_VELOCITY * 2f),
        ).isEqualTo(MiniSide.Left)
    }

    @Test
    fun `a fast fling right wins over a release in the left half`() {
        assertThat(
            snapMiniSide(releaseX = 100f, containerWidth = 1000f, velocityX = MINI_FLING_VELOCITY * 2f),
        ).isEqualTo(MiniSide.Right)
    }

    @Test
    fun `a slow release uses the release point`() {
        assertThat(
            snapMiniSide(releaseX = 100f, containerWidth = 1000f, velocityX = MINI_FLING_VELOCITY / 2f),
        ).isEqualTo(MiniSide.Left)
    }

    @Test
    fun `dragging past a third of the card dismisses it`() {
        assertThat(shouldDismissMini(offsetY = 60f, cardHeight = 100f, velocityY = 0f)).isTrue()
    }

    @Test
    fun `a small slow drag springs back`() {
        assertThat(shouldDismissMini(offsetY = 10f, cardHeight = 100f, velocityY = 100f)).isFalse()
    }

    @Test
    fun `a downward fling dismisses without travel`() {
        assertThat(shouldDismissMini(offsetY = 0f, cardHeight = 100f, velocityY = MINI_DISMISS_VELOCITY)).isTrue()
    }
}
