package dev.teyd.justintv.feature.watch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniSnapTest {
    @Test
    fun `portrait expanded slot is 16 by 9 under the status bar`() {
        val frame =
            expandedPlayerFrame(
                containerWidth = 1080f,
                containerHeight = 2400f,
                statusBar = 80f,
                landscape = false,
                chatWidth = 0f,
            )
        assertThat(frame.left).isEqualTo(0f)
        assertThat(frame.top).isEqualTo(80f)
        assertThat(frame.width).isEqualTo(1080f)
        assertThat(frame.height).isEqualTo(1080f * 9f / 16f)
    }

    @Test
    fun `landscape expanded slot leaves room for chat`() {
        val frame =
            expandedPlayerFrame(
                containerWidth = 2400f,
                containerHeight = 1080f,
                statusBar = 0f,
                landscape = true,
                chatWidth = 400f,
            )
        assertThat(frame.width).isEqualTo(2000f)
        assertThat(frame.height).isEqualTo(1080f)
    }

    @Test
    fun `the docked thumbnail sits above the navigation bar`() {
        val frame = dockedVideoFrame(containerHeight = 2400f, navigationBar = 60f, dockHeight = 160f)
        assertThat(frame.top).isEqualTo(2400f - 60f - 160f)
        assertThat(frame.height).isEqualTo(160f)
        assertThat(frame.width).isEqualTo(160f * 16f / 9f)
        assertThat(frame.left).isEqualTo(0f)
    }

    @Test
    fun `halfway between expanded and docked is the midpoint`() {
        val from = PlayerFrame(0f, 100f, 1000f, 500f)
        val to = PlayerFrame(0f, 2000f, 200f, 100f)
        val mid = lerpFrame(from, to, 0.5f)
        assertThat(mid.top).isEqualTo(1050f)
        assertThat(mid.width).isEqualTo(600f)
        assertThat(mid.height).isEqualTo(300f)
    }

    @Test
    fun `dragging past a third of the dock dismisses it`() {
        assertThat(shouldDismissMini(offsetY = 60f, dockHeight = 100f, velocityY = 0f)).isTrue()
    }

    @Test
    fun `a small slow drag springs back`() {
        assertThat(shouldDismissMini(offsetY = 10f, dockHeight = 100f, velocityY = 100f)).isFalse()
    }

    @Test
    fun `a downward fling dismisses without travel`() {
        assertThat(shouldDismissMini(offsetY = 0f, dockHeight = 100f, velocityY = MINI_DISMISS_VELOCITY)).isTrue()
    }

    @Test
    fun `a finger pixel moves the player by the same fraction of the travel`() {
        assertThat(applyPlayerDrag(progress = 0f, deltaY = 200f, travel = 800f)).isEqualTo(0.25f)
    }

    @Test
    fun `the player cannot be dragged above the expanded slot`() {
        assertThat(applyPlayerDrag(progress = 0.1f, deltaY = -500f, travel = 800f)).isEqualTo(0f)
    }

    @Test
    fun `a short drag on the full player springs back`() {
        assertThat(settlePlayerDrag(progress = 0.2f, velocityY = 0f)).isEqualTo(PlayerDragSettle.Expanded)
    }

    @Test
    fun `dragging past the commit point docks`() {
        assertThat(settlePlayerDrag(progress = 0.5f, velocityY = 0f)).isEqualTo(PlayerDragSettle.Mini)
    }

    @Test
    fun `a downward flick from the full player docks instead of dismissing`() {
        assertThat(settlePlayerDrag(progress = 0.2f, velocityY = COLLAPSE_FLING)).isEqualTo(PlayerDragSettle.Mini)
    }

    @Test
    fun `a downward flick from the dock dismisses`() {
        assertThat(settlePlayerDrag(progress = 1f, velocityY = MINI_DISMISS_VELOCITY)).isEqualTo(PlayerDragSettle.Dismiss)
    }

    @Test
    fun `pulling up from the dock expands`() {
        assertThat(settlePlayerDrag(progress = 0.8f, velocityY = -COLLAPSE_FLING)).isEqualTo(PlayerDragSettle.Expanded)
    }
}
