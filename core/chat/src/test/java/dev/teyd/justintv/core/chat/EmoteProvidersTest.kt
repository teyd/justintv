package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.network.TextFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EmoteProvidersTest {

    // Trimmed from real responses captured on 2026-10-05.

    private val sevenTvSet = """
        {"id":"x","emotes":[
          {"id":"01EZPGMA6G00047EF100A1SBTF","name":"TrollDespair","flags":0,
           "data":{"id":"01EZPGMA6G00047EF100A1SBTF","name":"TrollDespair","animated":false,
             "host":{"url":"//cdn.7tv.app/emote/01EZPGMA6G00047EF100A1SBTF","files":[
               {"name":"1x.webp","width":32,"height":32,"frame_count":1,"format":"WEBP"},
               {"name":"2x.webp","width":64,"height":64,"frame_count":1,"format":"WEBP"}]}}},
          {"id":"nohost","name":"Broken","flags":0,"data":{"id":"nohost","name":"Broken"}}
        ]}
    """.trimIndent()

    @Test
    fun `7tv set maps name url and size and skips emotes without a host`() {
        val emotes = SevenTvParser.parseSet(sevenTvSet)

        assertThat(emotes).hasSize(1)
        with(emotes.single()) {
            assertThat(name).isEqualTo("TrollDespair")
            assertThat(url).isEqualTo("https://cdn.7tv.app/emote/01EZPGMA6G00047EF100A1SBTF/2x.webp")
            assertThat(width).isEqualTo(64)
            assertThat(height).isEqualTo(64)
            assertThat(source).isEqualTo(EmoteSource.SevenTv)
        }
    }

    @Test
    fun `7tv user without an emote set yields nothing`() {
        assertThat(SevenTvParser.parseUser("""{"id":"1","username":"x"}""")).isEmpty()
        assertThat(SevenTvParser.parseUser("""{"emote_set":$sevenTvSet}""")).hasSize(1)
    }

    @Test
    fun `bttv global and channel`() {
        val global = BttvParser.parseGlobal(
            """[{"id":"54fa8f1401e468494b85b537","code":":tf:","imageType":"png","animated":false}]""",
        )
        assertThat(global.single().name).isEqualTo(":tf:")
        assertThat(global.single().url).isEqualTo("https://cdn.betterttv.net/emote/54fa8f1401e468494b85b537/2x.webp")

        val channel = BttvParser.parseUser(
            """{"channelEmotes":[{"id":"a","code":"caedJAMMER"}],"sharedEmotes":[{"id":"b","code":"Shared"}]}""",
        )
        assertThat(channel.map { it.name }).containsExactly("Shared", "caedJAMMER")
    }

    @Test
    fun `ffz global only uses the default sets`() {
        val body = """
            {"default_sets":[3],"sets":{
              "3":{"emoticons":[{"name":"ZrehplaR","width":33,"height":30,"urls":{"1":"https://cdn.frankerfacez.com/emote/9/1","2":"https://cdn.frankerfacez.com/emote/9/2"}}]},
              "99":{"emoticons":[{"name":"NotDefault","width":10,"height":10,"urls":{"1":"//x/1"}}]}}}
        """.trimIndent()

        val emotes = FfzParser.parseGlobal(body)

        assertThat(emotes.map { it.name }).containsExactly("ZrehplaR")
        assertThat(emotes.single().url).isEqualTo("https://cdn.frankerfacez.com/emote/9/2")
        assertThat(emotes.single().aspectRatio).isWithin(0.01f).of(33f / 30f)
    }

    @Test
    fun `ffz room takes every set and fixes protocol relative urls`() {
        val body = """{"room":{"set":840236},"sets":{"840236":{"emoticons":[{"name":"caedHi","width":28,"height":28,"urls":{"1":"//cdn.frankerfacez.com/emote/210748/1"}}]}}}"""

        val emotes = FfzParser.parseRoom(body)

        assertThat(emotes.single().url).isEqualTo("https://cdn.frankerfacez.com/emote/210748/1")
    }

    @Test
    fun `aspect ratio is clamped and defaults to square`() {
        assertThat(Emote("a", "u", null, null, EmoteSource.Bttv).aspectRatio).isEqualTo(1f)
        assertThat(Emote("a", "u", 1000, 10, EmoteSource.Bttv).aspectRatio).isEqualTo(4f)
        assertThat(Emote("a", "u", 10, 1000, EmoteSource.Bttv).aspectRatio).isEqualTo(0.5f)
    }

    // ------------------------------------------------------------ repository

    private class FakeProvider(
        private val global: List<Emote> = emptyList(),
        private val channel: List<Emote> = emptyList(),
        private val fail: Boolean = false,
        override val source: EmoteSource = EmoteSource.Bttv,
    ) : EmoteProvider {
        var globalCalls = 0
        override suspend fun global(): List<Emote> {
            globalCalls++
            if (fail) throw IllegalStateException("down")
            return global
        }

        override suspend fun channel(roomId: String): List<Emote> {
            if (fail) throw IllegalStateException("down")
            return channel
        }
    }

    private fun emote(name: String, source: EmoteSource, tag: String = "") =
        Emote(name, "https://x/$source$tag", source = source)

    @Test
    fun `channel emotes override global ones and 7tv wins over bttv`() = runTest {
        val bttv = FakeProvider(
            global = listOf(emote("Same", EmoteSource.Bttv, "-global")),
            channel = listOf(emote("Chan", EmoteSource.Bttv)),
        )
        val seven = FakeProvider(
            global = listOf(emote("Same", EmoteSource.SevenTv, "-global")),
            channel = listOf(emote("Chan", EmoteSource.SevenTv)),
        )

        val index = EmoteRepository(listOf(bttv, seven)).indexFor("1")

        assertThat(index["Same"]!!.source).isEqualTo(EmoteSource.SevenTv)
        assertThat(index["Chan"]!!.source).isEqualTo(EmoteSource.SevenTv)
    }

    @Test
    fun `a failing provider does not stop the others`() = runTest {
        val broken = FakeProvider(fail = true)
        val working = FakeProvider(global = listOf(emote("Ok", EmoteSource.Ffz)))

        val index = EmoteRepository(listOf(broken, working)).indexFor("1")

        assertThat(index["Ok"]).isNotNull()
    }

    @Test
    fun `global emotes are fetched once and reused`() = runTest {
        val provider = FakeProvider(global = listOf(emote("G", EmoteSource.Ffz)))
        val repository = EmoteRepository(listOf(provider))

        repository.indexFor("1")
        repository.indexFor("2")

        assertThat(provider.globalCalls).isEqualTo(1)
    }

    @Test
    fun `providers that are switched off are not queried`() = runTest {
        val bttv = FakeProvider(global = listOf(emote("B", EmoteSource.Bttv)), source = EmoteSource.Bttv)
        val seven = FakeProvider(global = listOf(emote("S", EmoteSource.SevenTv)), source = EmoteSource.SevenTv)
        val repository = EmoteRepository(
            providers = listOf(bttv, seven),
            enabledSources = flowOf(setOf(EmoteSource.Bttv)),
        )

        val index = repository.indexFor("1")

        assertThat(index["B"]).isNotNull()
        assertThat(index["S"]).isNull()
        assertThat(seven.globalCalls).isEqualTo(0)
    }

    @Test
    fun `turning a provider off refreshes the cached global set`() = runTest {
        val bttv = FakeProvider(global = listOf(emote("B", EmoteSource.Bttv)), source = EmoteSource.Bttv)
        val seven = FakeProvider(global = listOf(emote("S", EmoteSource.SevenTv)), source = EmoteSource.SevenTv)
        val enabled = MutableStateFlow(setOf(EmoteSource.Bttv, EmoteSource.SevenTv))
        val repository = EmoteRepository(providers = listOf(bttv, seven), enabledSources = enabled)

        assertThat(repository.indexFor("1")["S"]).isNotNull()

        enabled.value = setOf(EmoteSource.Bttv)
        val after = repository.indexFor("2")

        assertThat(after["B"]).isNotNull()
        assertThat(after["S"]).isNull()
    }

    @Test
    fun `a failed global load is retried next time`() = runTest {
        val provider = FakeProvider(fail = true)
        val repository = EmoteRepository(listOf(provider))

        repository.indexFor("1")
        repository.indexFor("2")

        assertThat(provider.globalCalls).isEqualTo(2)
    }

    @Test
    fun `no room id still yields global emotes`() = runTest {
        val provider = FakeProvider(global = listOf(emote("G", EmoteSource.SevenTv)))

        val index = EmoteRepository(listOf(provider)).indexFor(null)

        assertThat(index["G"]).isNotNull()
    }

    // ------------------------------------------------------------ provider wiring

    @Test
    fun `providers hit the documented urls`() = runTest {
        val requested = mutableListOf<String>()
        val fetcher = TextFetcher { url ->
            requested += url
            when {
                "7tv.io" in url -> """{"emotes":[]}"""
                "betterttv" in url && "global" in url -> "[]"
                "betterttv" in url -> """{"channelEmotes":[],"sharedEmotes":[]}"""
                else -> """{"default_sets":[],"sets":{}}"""
            }
        }

        listOf(SevenTvProvider(fetcher), BttvProvider(fetcher), FfzProvider(fetcher)).forEach {
            it.global()
            it.channel("92038375")
        }

        assertThat(requested).containsAtLeast(
            "https://7tv.io/v3/emote-sets/global",
            "https://7tv.io/v3/users/twitch/92038375",
            "https://api.betterttv.net/3/cached/emotes/global",
            "https://api.betterttv.net/3/cached/users/twitch/92038375",
            "https://api.frankerfacez.com/v1/set/global",
            "https://api.frankerfacez.com/v1/room/id/92038375",
        )
    }
}
