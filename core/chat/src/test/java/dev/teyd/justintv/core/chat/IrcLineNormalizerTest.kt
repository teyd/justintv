package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IrcLineNormalizerTest {
    @Test
    fun `inserts the missing privmsg colon`() {
        val line = "@id=1 :user!user@user.tmi.twitch.tv PRIVMSG #chan hello there"

        assertThat(IrcLineNormalizer.normalize(line))
            .isEqualTo("@id=1 :user!user@user.tmi.twitch.tv PRIVMSG #chan :hello there")
    }

    @Test
    fun `leaves a well formed line alone`() {
        val line = "@id=1 :user!user@user.tmi.twitch.tv PRIVMSG #chan :hello"

        assertThat(IrcLineNormalizer.normalize(line)).isEqualTo(line)
    }
}
