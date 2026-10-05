package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatSessionTest {
    @Test
    fun `reconnect backoff grows and then plateaus`() {
        assertThat((1..8).map { ChatSession.backoffMs(it) })
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L, 15_000L, 15_000L)
            .inOrder()
    }

    @Test
    fun `backoff never goes below one second`() {
        assertThat(ChatSession.backoffMs(0)).isEqualTo(1_000L)
    }
}
