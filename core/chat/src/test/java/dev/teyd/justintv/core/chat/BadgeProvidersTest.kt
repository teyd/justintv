package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChatBadge
import dev.teyd.justintv.core.model.ChatBadgeSource
import dev.teyd.justintv.core.network.TextFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class BadgeProvidersTest {
    // Trimmed from real responses captured on 2026-10-06.

    private val twitchBody =
        """
        {"data":{"badges":[
          {"setID":"subscriber","version":"12","imageURL":"https://static-cdn.jtvnw.net/badges/v1/sub12/3",
           "title":"12-Month Subscriber"},
          {"setID":"moderator","version":"1","imageURL":"https://static-cdn.jtvnw.net/badges/v1/mod/3","title":"Moderator"},
          {"setID":"broken","version":"1","imageURL":"","title":""}
        ]}}
        """.trimIndent()

    @Test
    fun `twitch graphql badges map set and version to the returned image`() {
        val badges = TwitchBadgeParser.parse(twitchBody)

        val subscriber = badges.getValue(BadgeIndex.key("subscriber", "12"))
        assertThat(subscriber.source).isEqualTo(ChatBadgeSource.Twitch)
        assertThat(subscriber.url).isEqualTo("https://static-cdn.jtvnw.net/badges/v1/sub12/3")
        assertThat(subscriber.title).isEqualTo("12-Month Subscriber")
        assertThat(badges.getValue(BadgeIndex.key("moderator", "1")).url)
            .isEqualTo("https://static-cdn.jtvnw.net/badges/v1/mod/3")
        // A version with no image at all is dropped instead of becoming an empty draw.
        assertThat(badges).doesNotContainKey(BadgeIndex.key("broken", "1"))
    }

    @Test
    fun `chatterino badges map users to the largest image`() {
        val body =
            """
            {"badges":[
              {"tooltip":"Chatterino Contributor",
               "image1":"https://fourtf.com/chatterino/badges/helper.png",
               "image2":"https://fourtf.com/chatterino/badges/helper2x.png",
               "image3":"https://fourtf.com/chatterino/badges/helper3x.png",
               "users":["103973901","25452828"]}
            ]}
            """.trimIndent()

        val users = ChatterinoBadgeParser.parse(body)

        assertThat(users.keys).containsExactly("103973901", "25452828")
        val badge = users.getValue("103973901").single()
        assertThat(badge.source).isEqualTo(ChatBadgeSource.Chatterino)
        assertThat(badge.url).isEqualTo("https://fourtf.com/chatterino/badges/helper3x.png")
        assertThat(badge.title).isEqualTo("Chatterino Contributor")
    }

    @Test
    fun `ffz badges map users and fix protocol relative urls`() {
        val body =
            """
            {"badges":[
              {"id":3,"name":"supporter","title":"FFZ Supporter","slot":5,
               "urls":{"1":"//cdn.frankerfacez.com/badge/3/1","2":"//cdn.frankerfacez.com/badge/3/2","4":"//cdn.frankerfacez.com/badge/3/4"}},
              {"id":1,"name":"developer","title":"FFZ Developer","slot":5,
               "urls":{"4":"https://cdn.frankerfacez.com/badge/1/4"}}
            ],"users":{"3":[47852576],"1":[47852576]}}
            """.trimIndent()

        val users = FfzBadgeParser.parse(body)

        val badges = users.getValue("47852576")
        assertThat(badges.map { it.title }).containsExactly("FFZ Supporter", "FFZ Developer")
        assertThat(badges.first().url).isEqualTo("https://cdn.frankerfacez.com/badge/3/4")
        assertThat(badges.map { it.source }.distinct()).containsExactly(ChatBadgeSource.Ffz)
    }

    @Test
    fun `bttv badges map provider ids to their svg`() {
        val body =
            """
            [{"id":"5533513462b6bd2027aee68d","name":"someguy","displayName":"SomeGuy",
              "providerId":"123456789",
              "badge":{"description":"NightDev Support Volunteer",
                       "svg":"https://cdn.betterttv.net/badges/support_volunteer.svg","type":2}}]
            """.trimIndent()

        val users = BttvBadgeParser.parse(body)

        val badge = users.getValue("123456789").single()
        assertThat(badge.source).isEqualTo(ChatBadgeSource.Bttv)
        assertThat(badge.url).isEqualTo("https://cdn.betterttv.net/badges/support_volunteer.svg")
        assertThat(badge.title).isEqualTo("NightDev Support Volunteer")
    }

    // ------------------------------------------------------------ repository

    private class FakeProvider(
        private val global: BadgeData = BadgeData(),
        private val channel: BadgeData = BadgeData(),
        private val fail: Boolean = false,
        override val source: ChatBadgeSource = ChatBadgeSource.Bttv,
    ) : BadgeProvider {
        var globalCalls = 0

        override suspend fun global(): BadgeData {
            globalCalls++
            if (fail) throw IllegalStateException("down")
            return global
        }

        override suspend fun channel(roomId: String): BadgeData {
            if (fail) throw IllegalStateException("down")
            return channel
        }
    }

    private fun badge(
        source: ChatBadgeSource,
        tag: String = "",
    ) = ChatBadge(source, "https://x/$source$tag", "$source$tag")

    private fun userBadges(
        source: ChatBadgeSource,
        userId: String,
    ) = BadgeData(users = mapOf(userId to listOf(badge(source))))

    @Test
    fun `a channel badge overrides the global one for the same set and version`() =
        runTest {
            val global = BadgeData(sets = mapOf(BadgeIndex.key("subscriber", "1") to badge(ChatBadgeSource.Twitch, "-global")))
            val channel = BadgeData(sets = mapOf(BadgeIndex.key("subscriber", "1") to badge(ChatBadgeSource.Twitch, "-channel")))
            val twitch =
                FakeProvider(global = global, channel = channel, source = ChatBadgeSource.Twitch)

            val index = BadgeRepository(listOf(twitch)).indexFor("1")

            assertThat(index.twitch("subscriber", "1")!!.url).endsWith("-channel")
        }

    @Test
    fun `user badges from several sources are all kept, ordered by source`() =
        runTest {
            val bttv = FakeProvider(global = userBadges(ChatBadgeSource.Bttv, "7"), source = ChatBadgeSource.Bttv)
            val ffz = FakeProvider(global = userBadges(ChatBadgeSource.Ffz, "7"), source = ChatBadgeSource.Ffz)

            val index = BadgeRepository(listOf(ffz, bttv)).indexFor("1")

            assertThat(index.user("7").map { it.source })
                .containsExactly(ChatBadgeSource.Ffz, ChatBadgeSource.Bttv)
                .inOrder()
        }

    @Test
    fun `a failing provider does not stop the others`() =
        runTest {
            val broken = FakeProvider(fail = true)
            val working = FakeProvider(global = userBadges(ChatBadgeSource.Ffz, "1"))

            val index = BadgeRepository(listOf(broken, working)).indexFor("1")

            assertThat(index.user("1")).isNotEmpty()
        }

    @Test
    fun `global badge sets are fetched once and reused`() =
        runTest {
            val provider = FakeProvider(global = userBadges(ChatBadgeSource.Ffz, "1"))
            val repository = BadgeRepository(listOf(provider))

            repository.indexFor("1")
            repository.indexFor("2")

            assertThat(provider.globalCalls).isEqualTo(1)
        }

    @Test
    fun `providers that are switched off are not queried`() =
        runTest {
            val bttv = FakeProvider(global = userBadges(ChatBadgeSource.Bttv, "1"), source = ChatBadgeSource.Bttv)
            val ffz = FakeProvider(global = userBadges(ChatBadgeSource.Ffz, "1"), source = ChatBadgeSource.Ffz)
            val repository =
                BadgeRepository(
                    providers = listOf(bttv, ffz),
                    enabledSources = flowOf(setOf(ChatBadgeSource.Bttv)),
                )

            val index = repository.indexFor("1")

            assertThat(index.user("1").map { it.source }).containsExactly(ChatBadgeSource.Bttv)
            assertThat(ffz.globalCalls).isEqualTo(0)
        }

    @Test
    fun `turning a provider off refreshes the cached global set`() =
        runTest {
            val bttv = FakeProvider(global = userBadges(ChatBadgeSource.Bttv, "1"), source = ChatBadgeSource.Bttv)
            val ffz = FakeProvider(global = userBadges(ChatBadgeSource.Ffz, "1"), source = ChatBadgeSource.Ffz)
            val enabled = MutableStateFlow(setOf(ChatBadgeSource.Bttv, ChatBadgeSource.Ffz))
            val repository = BadgeRepository(providers = listOf(bttv, ffz), enabledSources = enabled)

            assertThat(repository.indexFor("1").user("1").map { it.source })
                .containsExactly(ChatBadgeSource.Ffz, ChatBadgeSource.Bttv)
                .inOrder()

            enabled.value = setOf(ChatBadgeSource.Bttv)
            val after = repository.indexFor("2")

            assertThat(after.user("1").map { it.source }).containsExactly(ChatBadgeSource.Bttv)
        }

    @Test
    fun `a failed global load is retried next time`() =
        runTest {
            val provider = FakeProvider(fail = true)
            val repository = BadgeRepository(listOf(provider))

            repository.indexFor("1")
            repository.indexFor("2")

            assertThat(provider.globalCalls).isEqualTo(2)
        }

    @Test
    fun `no room id still yields global badges`() =
        runTest {
            val provider = FakeProvider(global = userBadges(ChatBadgeSource.Ffz, "1"))

            val index = BadgeRepository(listOf(provider)).indexFor(null)

            assertThat(index.user("1")).isNotEmpty()
        }

    @Test
    fun `the 7tv flag follows the switches`() =
        runTest {
            val repository = BadgeRepository(emptyList())
            assertThat(repository.indexFor("1").sevenTv).isTrue()

            val off =
                BadgeRepository(emptyList(), enabledSources = flowOf(setOf(ChatBadgeSource.Bttv)))
            assertThat(off.indexFor("1").sevenTv).isFalse()
        }

    // ------------------------------------------------------------ provider wiring

    @Test
    fun `user badge providers hit the documented urls`() =
        runTest {
            val requested = mutableListOf<String>()
            val fetcher =
                TextFetcher { url ->
                    requested += url
                    when {
                        "chatterino" in url -> """{"badges":[]}"""
                        "frankerfacez" in url -> """{"badges":[],"users":{}}"""
                        else -> "[]"
                    }
                }

            listOf(ChatterinoBadgeProvider(fetcher), FfzBadgeProvider(fetcher), BttvBadgeProvider(fetcher)).forEach {
                it.global()
            }

            assertThat(requested).containsExactly(
                "https://api.chatterino.com/badges",
                "https://api.frankerfacez.com/v1/badges/ids",
                "https://api.betterttv.net/3/cached/badges/twitch",
            )
        }

    @Test
    fun `twitch badge provider queries global and channel badges without credentials`() =
        runTest {
            val requested = mutableListOf<String>()
            val channelBody =
                """{"data":{"user":{"broadcastBadges":[
                  {"setID":"subscriber","version":"12","title":"Channel Subscriber","imageURL":"https://x/channel"}
                ]}}}"""
            val provider =
                TwitchBadgeProvider(
                    post = { body ->
                        val query =
                            Json
                                .parseToJsonElement(body)
                                .jsonObject
                                .getValue("query")
                                .jsonPrimitive.content
                        requested += query
                        if ("broadcastBadges" in query) channelBody else twitchBody
                    },
                )

            assertThat(provider.global().sets).containsKey("moderator/1")
            assertThat(
                provider
                    .channel("92038375")
                    .sets
                    .getValue("subscriber/12")
                    .url,
            ).isEqualTo("https://x/channel")

            assertThat(requested).containsExactly(
                "query { badges { setID version title imageURL(size: QUADRUPLE) } }",
                "query { user(id: \"92038375\") { broadcastBadges { setID version title imageURL(size: QUADRUPLE) } } }",
            )
            assertThat(provider.channel("").isEmpty).isTrue()
            assertThat(requested).hasSize(2)
        }

    @Test
    fun `unknown twitch channel has no custom badges`() {
        assertThat(TwitchBadgeParser.parse("""{"data":{"user":null}}""", channel = true)).isEmpty()
    }

    @Test
    fun `twitch graphql errors including partial data throw instead of caching`() {
        for (body in listOf(
            """{"errors":[{"message":"failed"}]}""",
            """{"data":{"badges":[]},"errors":[{"message":"partial failure"}]}""",
            """{"data":{}}""",
            "not json",
        )) {
            assertThat(runCatching { TwitchBadgeParser.parse(body) }.isFailure).isTrue()
        }
    }

    @Test
    fun `failed twitch graphql load is retried and never falls back`() =
        runTest {
            var calls = 0
            val provider =
                TwitchBadgeProvider {
                    calls++
                    if (calls == 1) """{"errors":[{"message":"temporarily unavailable"}]}""" else twitchBody
                }
            val repository = BadgeRepository(listOf(provider))

            assertThat(repository.indexFor(null).twitch("moderator", "1")).isNull()
            assertThat(repository.indexFor(null).twitch("moderator", "1")).isNotNull()
            assertThat(calls).isEqualTo(2)
        }
}
