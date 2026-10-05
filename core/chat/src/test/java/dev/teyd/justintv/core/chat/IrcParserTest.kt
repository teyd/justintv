package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IrcParserTest {

    private val privmsg =
        "@badge-info=;color=#FF0000;display-name=Some\\sUser;emotes=25:0-4;id=abc-123;room-id=92038375;tmi-sent-ts=1 " +
            ":someuser!someuser@someuser.tmi.twitch.tv PRIVMSG #caedrel :Kappa hello there"

    @Test
    fun `parses tags prefix command params and trailing`() {
        val message = IrcParser.parse(privmsg)!!

        assertThat(message.command).isEqualTo("PRIVMSG")
        assertThat(message.params).containsExactly("#caedrel")
        assertThat(message.trailing).isEqualTo("Kappa hello there")
        assertThat(message.nick).isEqualTo("someuser")
        assertThat(message.tags["id"]).isEqualTo("abc-123")
        assertThat(message.tags["room-id"]).isEqualTo("92038375")
    }

    @Test
    fun `unescapes tag values`() {
        val message = IrcParser.parse(privmsg)!!

        assertThat(message.tags["display-name"]).isEqualTo("Some User")
        assertThat(IrcParser.parse("@a=x\\:y\\\\z\\sw :s CMD")!!.tags["a"]).isEqualTo("x;y\\z w")
    }

    @Test
    fun `keeps colons inside the trailing text`() {
        val message = IrcParser.parse(":n!n@n PRIVMSG #c :look: a url https://x.y/z")!!

        assertThat(message.trailing).isEqualTo("look: a url https://x.y/z")
    }

    @Test
    fun `parses a roomstate with a room id`() {
        val message = IrcParser.parse("@emote-only=0;room-id=92038375;slow=0 :tmi.twitch.tv ROOMSTATE #caedrel")!!

        assertThat(message.command).isEqualTo("ROOMSTATE")
        assertThat(message.tags["room-id"]).isEqualTo("92038375")
        assertThat(message.trailing).isNull()
    }

    @Test
    fun `parses a ping`() {
        val message = IrcParser.parse("PING :tmi.twitch.tv")!!

        assertThat(message.command).isEqualTo("PING")
        assertThat(message.trailing).isEqualTo("tmi.twitch.tv")
    }

    @Test
    fun `ignores blank and malformed lines`() {
        assertThat(IrcParser.parse("")).isNull()
        assertThat(IrcParser.parse("   ")).isNull()
        assertThat(IrcParser.parse("@tags-only")).isNull()
        assertThat(IrcParser.parse(":prefix-only")).isNull()
    }

    @Test
    fun `handles a tag without a value`() {
        assertThat(IrcParser.parse("@flag;k=v :s CMD")!!.tags).containsExactly("flag", "", "k", "v")
    }
}
