package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChatSegment
import org.junit.Test

class ChatMessageParserTest {
    private val index =
        EmoteIndex
            .Builder()
            .addAll(
                listOf(
                    Emote("KEKW", "https://cdn.7tv.app/emote/kekw/2x.webp", 64, 64, EmoteSource.SevenTv),
                    Emote("widepeepo", "https://cdn.7tv.app/emote/wide/2x.webp", 128, 32, EmoteSource.SevenTv),
                    Emote("OMEGALUL", "https://cdn.betterttv.net/emote/omg/2x.webp", source = EmoteSource.Bttv),
                ),
            ).build()

    private fun privmsg(
        text: String,
        tags: String = "display-name=Viewer;id=m1",
    ): IrcMessage = IrcParser.parse("@$tags :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #chan :$text")!!

    @Test
    fun `plain text stays one text segment`() {
        val message = ChatMessageParser.parse(privmsg("hello world"), index)!!

        assertThat(message.user).isEqualTo("Viewer")
        assertThat(message.segments).containsExactly(ChatSegment.Text("hello world"))
    }

    @Test
    fun `third party emotes are matched by whole word`() {
        val message = ChatMessageParser.parse(privmsg("that was KEKW honestly"), index)!!

        assertThat(message.segments).hasSize(3)
        assertThat(message.segments[0]).isEqualTo(ChatSegment.Text("that was "))
        val emote = message.segments[1] as ChatSegment.Emote
        assertThat(emote.name).isEqualTo("KEKW")
        assertThat(emote.url).contains("kekw")
        assertThat(message.segments[2]).isEqualTo(ChatSegment.Text(" honestly"))
    }

    @Test
    fun `a name inside a longer word is not an emote`() {
        val message = ChatMessageParser.parse(privmsg("KEKWait and xKEKW"), index)!!

        assertThat(message.segments).containsExactly(ChatSegment.Text("KEKWait and xKEKW"))
    }

    @Test
    fun `emote names are case sensitive`() {
        val message = ChatMessageParser.parse(privmsg("kekw"), index)!!

        assertThat(message.segments).containsExactly(ChatSegment.Text("kekw"))
    }

    @Test
    fun `wide emotes keep their aspect ratio`() {
        val message = ChatMessageParser.parse(privmsg("widepeepo"), index)!!

        assertThat((message.segments.single() as ChatSegment.Emote).aspectRatio).isEqualTo(4f)
    }

    @Test
    fun `consecutive emotes keep their separating space`() {
        val message = ChatMessageParser.parse(privmsg("KEKW KEKW OMEGALUL"), index)!!

        assertThat(message.segments.map { it::class })
            .containsExactly(
                ChatSegment.Emote::class,
                ChatSegment.Text::class,
                ChatSegment.Emote::class,
                ChatSegment.Text::class,
                ChatSegment.Emote::class,
            ).inOrder()
    }

    @Test
    fun `twitch native emotes come from the tag ranges`() {
        val message =
            ChatMessageParser.parse(
                privmsg("Kappa hello Kappa", tags = "display-name=V;id=m2;emotes=25:0-4,12-16"),
                EmoteIndex.EMPTY,
            )!!

        val emotes = message.segments.filterIsInstance<ChatSegment.Emote>()
        assertThat(emotes.map { it.name }).containsExactly("Kappa", "Kappa")
        assertThat(emotes.first().url).isEqualTo("https://static-cdn.jtvnw.net/emoticons/v2/25/default/dark/2.0")
        assertThat((message.segments[1] as ChatSegment.Text).text).isEqualTo(" hello ")
    }

    @Test
    fun `twitch ranges and third party emotes mix in one message`() {
        val message =
            ChatMessageParser.parse(
                privmsg("Kappa KEKW Kappa", tags = "display-name=V;id=m3;emotes=25:0-4,11-15"),
                index,
            )!!

        assertThat(message.segments.filterIsInstance<ChatSegment.Emote>().map { it.name })
            .containsExactly("Kappa", "KEKW", "Kappa")
            .inOrder()
    }

    @Test
    fun `ranges count code points, not utf16 units`() {
        // The party popper is one code point but two UTF-16 chars, so Kappa starts at index 2.
        val message =
            ChatMessageParser.parse(
                privmsg("\uD83C\uDF89 Kappa", tags = "display-name=V;id=m4;emotes=25:2-6"),
                EmoteIndex.EMPTY,
            )!!

        assertThat(
            message.segments
                .filterIsInstance<ChatSegment.Emote>()
                .single()
                .name,
        ).isEqualTo("Kappa")
        assertThat((message.segments.first() as ChatSegment.Text).text).isEqualTo("\uD83C\uDF89 ")
    }

    @Test
    fun `bad ranges are ignored instead of crashing`() {
        val message =
            ChatMessageParser.parse(
                privmsg("short", tags = "display-name=V;id=m5;emotes=25:40-50/1:3-1/9:abc"),
                EmoteIndex.EMPTY,
            )!!

        assertThat(message.segments).containsExactly(ChatSegment.Text("short"))
    }

    @Test
    fun `me actions are unwrapped and flagged`() {
        val message = ChatMessageParser.parse(privmsg("\u0001ACTION waves KEKW\u0001"), index)!!

        assertThat(message.isAction).isTrue()
        assertThat(message.segments.first()).isEqualTo(ChatSegment.Text("waves "))
        assertThat(message.segments.last()).isInstanceOf(ChatSegment.Emote::class.java)
    }

    @Test
    fun `falls back to the nick when there is no display name`() {
        val message = ChatMessageParser.parse(privmsg("hi", tags = "id=m6"), EmoteIndex.EMPTY)!!

        assertThat(message.user).isEqualTo("viewer")
    }

    @Test
    fun `only chat lines become messages`() {
        val roomstate = IrcParser.parse("@room-id=1 :tmi.twitch.tv ROOMSTATE #chan")!!

        assertThat(ChatMessageParser.parse(roomstate, index)).isNull()
    }

    @Test
    fun `parses the emotes tag`() {
        val ranges = ChatMessageParser.parseEmoteTag("1902:6-10/25:0-4,12-16")

        assertThat(ranges.map { it.id to it.start }).containsExactly("25" to 0, "1902" to 6, "25" to 12).inOrder()
        assertThat(ChatMessageParser.parseEmoteTag(null)).isEmpty()
        assertThat(ChatMessageParser.parseEmoteTag("")).isEmpty()
    }
}
