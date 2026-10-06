package dev.teyd.justintv.core.chat

import dev.teyd.justintv.core.model.ChatSegment
import dev.teyd.justintv.core.network.GqlClient
import dev.teyd.justintv.core.network.JsonPoster
import dev.teyd.justintv.core.network.OkHttpTextFetcher
import dev.teyd.justintv.core.network.TwitchDirectoryApi
import dev.teyd.justintv.core.network.TwitchHttpClient
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real chat, real emote providers. Skipped unless `JUSTINTV_LIVE=1`:
 *
 *     JUSTINTV_LIVE=1 ./gradlew :core:chat:testDebugUnitTest --tests '*LiveChatSmokeTest*'
 */
class LiveChatSmokeTest {
    private val base = TwitchHttpClient.create()

    @Test
    fun `receives real messages with resolved emotes from a busy channel`() =
        runBlocking {
            assumeTrue(System.getenv("JUSTINTV_LIVE") == "1")

            val fetcher = OkHttpTextFetcher(base)
            val repository =
                EmoteRepository(
                    listOf(SevenTvProvider(fetcher), BttvProvider(fetcher), FfzProvider(fetcher)),
                )
            val channel = TwitchDirectoryApi(GqlClient(base)).topStreams(emptySet()).first().login
            val session =
                ChatSession(
                    irc = TwitchIrcClient(base),
                    emoteRepository = repository,
                    badgeRepository = BadgeRepository(emptyList(), enabledSources = flowOf(emptySet())),
                    sevenTvBadges = SevenTvBadges(JsonPoster { _, _ -> """{"data":{"users":{}}}""" }),
                    recent = RecentMessages(base),
                )

            val messages =
                withTimeout(60_000) {
                    session
                        .messages(channel)
                        .filterIsInstance<ChatEvent.New>()
                        .map { it.message }
                        .take(80)
                        .toList()
                }

            val emotes = messages.flatMap { it.segments }.filterIsInstance<ChatSegment.Emote>()
            val hosts = emotes.map { it.url.substringAfter("://").substringBefore('/') }.groupingBy { it }.eachCount()
            println("LIVE chat channel=$channel messages=${messages.size} status=${session.connection.value.status}")
            println("LIVE chat emotes=${emotes.size} byHost=$hosts")
            println(
                "LIVE chat sample: " +
                    messages.take(5).joinToString(" | ") { m ->
                        m.user + ": " +
                            m.segments.joinToString("") { s ->
                                when (s) {
                                    is ChatSegment.Text -> s.text
                                    is ChatSegment.Emote -> "[${s.name}]"
                                }
                            }
                    },
            )
            check(messages.size == 80) { "expected 80 messages, got ${messages.size}" }
            check(emotes.isNotEmpty()) { "no emotes resolved in 80 messages" }
        }

    @Test
    fun `all three providers return emotes for a real channel`() =
        runBlocking {
            assumeTrue(System.getenv("JUSTINTV_LIVE") == "1")

            val fetcher = OkHttpTextFetcher(base)
            val roomId = "92038375"
            listOf("7tv" to SevenTvProvider(fetcher), "bttv" to BttvProvider(fetcher), "ffz" to FfzProvider(fetcher))
                .forEach { (name, provider) ->
                    val global = provider.global()
                    val channel = runCatching { provider.channel(roomId) }.getOrDefault(emptyList())
                    println("LIVE emotes $name global=${global.size} channel=${channel.size} sample=${global.firstOrNull()?.url}")
                    check(global.isNotEmpty()) { "$name returned no global emotes" }
                }
        }
}
