package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.Game
import dev.teyd.justintv.core.model.LiveStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class HelixDirectoryTest {
    private val streamsBody =
        """
        {"data":[
          {"id":"1","user_id":"10","user_login":"alice","user_name":"Alice","title":"Hi","viewer_count":42,
           "thumbnail_url":"https://x/live_user_alice-{width}x{height}.jpg","game_name":"Chess","language":"de",
           "started_at":"2026-01-01T00:00:00Z"},
          {"id":"2","user_id":"11","user_login":"bob","user_name":"","viewer_count":1}
        ],"pagination":{"cursor":"abc"}}
        """.trimIndent()

    @Test
    fun `streams map with avatars and cursor`() {
        val page = HelixDirectoryMapper.parseStreams(streamsBody, mapOf("10" to "https://x/a.png"))
        assertThat(page.cursor).isEqualTo("abc")
        val first = page.items[0]
        assertThat(first.login).isEqualTo("alice")
        assertThat(first.viewerCount).isEqualTo(42)
        assertThat(first.previewUrl).isEqualTo("https://x/live_user_alice-440x248.jpg")
        assertThat(first.avatarUrl).isEqualTo("https://x/a.png")
        assertThat(first.language).isEqualTo("DE")
        assertThat(page.items[1].displayName).isEqualTo("bob")
        assertThat(page.items[1].avatarUrl).isNull()
    }

    @Test
    fun `missing or empty pagination means last page`() {
        assertThat(HelixDirectoryMapper.parseStreams("""{"data":[],"pagination":{}}""").cursor).isNull()
        assertThat(HelixDirectoryMapper.parseStreams("""{"data":[],"pagination":{"cursor":""}}""").cursor).isNull()
        assertThat(HelixDirectoryMapper.parseStreams("""{"data":[]}""").cursor).isNull()
    }

    @Test
    fun `garbage throws a helix exception`() {
        assertThrows(HelixException::class.java) { HelixDirectoryMapper.parseStreams("nope") }
    }

    @Test
    fun `games map with zero viewers and sized box art`() {
        val page =
            HelixDirectoryMapper.parseGames(
                """{"data":[{"id":"7","name":"Chess","box_art_url":"https://x/{width}x{height}.jpg"}],"pagination":{"cursor":"c2"}}""",
            )
        assertThat(page.items).containsExactly(Game("7", "Chess", "Chess", "https://x/285x380.jpg", 0))
        assertThat(page.cursor).isEqualTo("c2")
    }

    @Test
    fun `game id lookup`() {
        assertThat(HelixDirectoryMapper.parseGameId("""{"data":[{"id":"7","name":"Chess"}]}""")).isEqualTo("7")
        assertThat(HelixDirectoryMapper.parseGameId("""{"data":[]}""")).isNull()
    }

    @Test
    fun `avatars skip users without a picture`() {
        val map =
            HelixDirectoryMapper.parseAvatars(
                """{"data":[{"id":"1","profile_image_url":"u"},{"id":"2"},{"id":"3","profile_image_url":""}]}""",
            )
        assertThat(map).containsExactly("1", "u")
    }

    @Test
    fun `streams url carries languages game and cursor`() {
        val url = HelixDirectoryMapper.streamsUrl(setOf("EN", "PT_BR", "en", "x]}"), gameId = "7", after = "a b", first = 500)
        assertThat(url).startsWith("https://api.twitch.tv/helix/streams?first=100")
        assertThat(url).contains("&language=en&language=pt")
        assertThat(url.split("language=en").size).isEqualTo(2)
        assertThat(url).contains("&game_id=7")
        assertThat(url).endsWith("&after=a+b")
        assertThat(url).doesNotContain("x%5D")
    }

    @Test
    fun `first page url has no after`() {
        assertThat(HelixDirectoryMapper.streamsUrl(emptySet())).isEqualTo("https://api.twitch.tv/helix/streams?first=100")
        assertThat(HelixDirectoryMapper.followedUrl("5", "cur"))
            .isEqualTo("https://api.twitch.tv/helix/streams/followed?user_id=5&first=100&after=cur")
        assertThat(HelixDirectoryMapper.topGamesUrl(null, first = 0)).endsWith("games/top?first=1")
    }

    @Test
    fun `users url dedupes and caps at 100`() {
        assertThat(HelixDirectoryMapper.usersUrl(listOf("", " ".trim()))).isNull()
        val url = HelixDirectoryMapper.usersUrl((1..150).map { (it % 120).toString() } + "")!!
        assertThat(url.split("id=").size - 1).isEqualTo(100)
    }

    @Test
    fun `retry delay prefers Retry-After then Ratelimit-Reset`() {
        assertThat(HelixRateLimit.retryDelayMs("2", "9999999999", 0L)).isEqualTo(2000L)
        assertThat(HelixRateLimit.retryDelayMs(null, "103", 100_000L)).isEqualTo(3000L)
        assertThat(HelixRateLimit.retryDelayMs(null, "90", 100_000L)).isEqualTo(0L)
        assertThat(HelixRateLimit.retryDelayMs(null, null, 0L)).isNull()
        assertThat(HelixRateLimit.retryDelayMs("60", null, 0L)).isNull()
        assertThat(HelixRateLimit.retryDelayMs("soon", null, 0L)).isNull()
    }

    @Test
    fun `a 429 waits once then succeeds`() =
        runBlocking<Unit> {
            val results = ArrayDeque(listOf(HelixHttpResult(429, "", retryAfter = "1"), HelixHttpResult(200, "ok")))
            val sleeps = mutableListOf<Long>()
            val body = helixRequest(nowMs = { 0L }, sleep = { sleeps += it }) { results.removeFirst() }
            assertThat(body).isEqualTo("ok")
            assertThat(sleeps).containsExactly(1000L)
        }

    @Test
    fun `a second 429 fails cleanly`() {
        var calls = 0
        assertThrows(HelixRateLimitedException::class.java) {
            runBlocking {
                helixRequest(nowMs = { 0L }, sleep = {}) {
                    calls++
                    HelixHttpResult(429, "", retryAfter = "0")
                }
            }
        }
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `a 429 with no usable wait is not retried`() {
        var calls = 0
        assertThrows(HelixRateLimitedException::class.java) {
            runBlocking {
                helixRequest(sleep = {}) {
                    calls++
                    HelixHttpResult(429, "")
                }
            }
        }
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `401 and other errors map to exceptions`() {
        assertThrows(UnauthorizedException::class.java) { runBlocking { helixRequest { HelixHttpResult(401, "") } } }
        val e = assertThrows(HelixException::class.java) { runBlocking { helixRequest { HelixHttpResult(500, """{"message":"boom"}""") } } }
        assertThat(e).hasMessageThat().isEqualTo("boom")
    }

    private class FakeAuth(
        override var isSignedIn: Boolean,
    ) : HelixAuthorizer {
        override suspend fun <T> authorized(block: suspend (HelixCredentials) -> T): T = block(HelixCredentials("c", "t", "u"))
    }

    private class FakeGql : DirectorySource {
        override suspend fun topStreams(languages: Set<String>) = listOf(stream("gql"))

        override suspend fun gameStreams(
            gameName: String,
            languages: Set<String>,
        ) = listOf(stream("gql-game"))

        override suspend fun topGames() = emptyList<Game>()

        override suspend fun channelLive(login: String): ChannelLive? = null

        override suspend fun searchChannels(query: String) = emptyList<dev.teyd.justintv.core.model.ChannelHit>()
    }

    private class FakeHelix(
        var fail: Exception? = null,
    ) : HelixDirectory {
        override suspend fun streams(
            languages: Set<String>,
            gameId: String?,
            after: String?,
        ): Page<LiveStream> {
            fail?.let { throw it }
            return Page(listOf(stream("helix")), "next")
        }

        override suspend fun topGames(after: String?) = Page(emptyList<Game>())

        override suspend fun gameId(gameName: String): String? = "7"

        override suspend fun followedStreams(after: String?) = Page(emptyList<LiveStream>())
    }

    @Test
    fun `signed in uses helix with its cursor`() =
        runBlocking<Unit> {
            val source = CompositeDirectorySource(FakeGql(), FakeHelix(), FakeAuth(true))
            val page = source.topStreamsPage(emptySet())
            assertThat(page.items.single().id).isEqualTo("helix")
            assertThat(page.cursor).isEqualTo("next")
        }

    @Test
    fun `signed out uses graphql`() =
        runBlocking<Unit> {
            val source = CompositeDirectorySource(FakeGql(), FakeHelix(), FakeAuth(false))
            assertThat(source.topStreams(emptySet()).single().id).isEqualTo("gql")
            assertThat(source.gameStreams("Chess", emptySet()).single().id).isEqualTo("gql-game")
        }

    @Test
    fun `helix failure on the first page falls back to graphql`() =
        runBlocking<Unit> {
            val source = CompositeDirectorySource(FakeGql(), FakeHelix(HelixRateLimitedException()), FakeAuth(true))
            assertThat(source.topStreams(emptySet()).single().id).isEqualTo("gql")
        }

    @Test
    fun `helix failure on a later page throws instead of repeating page one`() {
        val source = CompositeDirectorySource(FakeGql(), FakeHelix(HelixException("x")), FakeAuth(true))
        assertThrows(HelixException::class.java) { runBlocking { source.topStreamsPage(emptySet(), after = "next") } }
    }

    private companion object {
        fun stream(id: String) = LiveStream(id, id, id, "", 0, null, null, null, null)
    }
}
