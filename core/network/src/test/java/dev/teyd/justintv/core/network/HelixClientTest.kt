package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class HelixClientTest {
    private val twitch = FakeTwitch()
    private val helix = twitch.helix

    @Test
    fun `currentUser parses the first Helix user and sends both auth headers`() =
        runTest {
            twitch.on("helix/users", 200, usersJson(userJson(id = "9", login = "abc", displayName = "ABC")))

            val user = helix.currentUser(TEST_CLIENT_ID, "tok")

            assertThat(user).isEqualTo(TwitchUser("9", "abc", "ABC"))
            val sent = twitch.requests.single()
            assertThat(sent.headers["Authorization"]).isEqualTo("Bearer tok")
            assertThat(sent.headers["Client-Id"]).isEqualTo(TEST_CLIENT_ID)
        }

    @Test
    fun `currentUser falls back to the login when there is no display name`() =
        runTest {
            twitch.on("helix/users", 200, usersJson(userJson(login = "abc", displayName = null)))
            assertThat(helix.currentUser(TEST_CLIENT_ID, "tok").displayName).isEqualTo("abc")
        }

    @Test
    fun `currentUser maps 401 to Unauthorized, other codes to a message, and no data to an error`() =
        runTest {
            twitch.on("helix/users", 401, """{"message":"Invalid OAuth token"}""")
            assertThrows(UnauthorizedException::class.java) { runBlockingCurrentUser() }

            val failing = FakeTwitch().apply { on("helix/users", 503, """{"message":"overloaded"}""") }
            val error = assertThrows(IdentityException::class.java) { failing.currentUserBlocking() }
            assertThat(error).isNotInstanceOf(UnauthorizedException::class.java)
            assertThat(error).hasMessageThat().isEqualTo("overloaded")

            val empty = FakeTwitch().apply { on("helix/users", 200, usersJson()) }
            assertThat(assertThrows(IdentityException::class.java) { empty.currentUserBlocking() })
                .hasMessageThat()
                .isEqualTo("Twitch returned no account")
        }

    @Test
    fun `followedStreams parses streams and fills avatars with one batched users call`() =
        runTest {
            twitch.on(
                "helix/streams/followed",
                200,
                streamsJson(streamJson("s1", "u1", "one", name = "One"), streamJson("s2", "u2", "two")),
            )
            twitch.on(
                "helix/users",
                200,
                usersJson(userJson("u1", "one", avatar = "https://img/one.png"), userJson("u2", "two")),
            )

            val streams = helix.followedStreams(TEST_CLIENT_ID, "tok", "42")

            assertThat(streams.map { it.login }).containsExactly("one", "two").inOrder()
            val first = streams[0]
            assertThat(first.displayName).isEqualTo("One")
            assertThat(first.title).isEqualTo("Playing")
            assertThat(first.viewerCount).isEqualTo(7)
            assertThat(first.gameName).isEqualTo("Chess")
            assertThat(first.previewUrl).isEqualTo("https://img/440x248.jpg")
            assertThat(first.avatarUrl).isEqualTo("https://img/one.png")
            assertThat(streams[1].displayName).isEqualTo("two")
            assertThat(streams[1].avatarUrl).isNull()
            assertThat(twitch.requestsTo("streams/followed").single().url).contains("user_id=42&first=100")
            val avatarCall = twitch.requestsTo("helix/users").single().url
            assertThat(avatarCall).contains("id=u1")
            assertThat(avatarCall).contains("id=u2")
        }

    @Test
    fun `followedStreams with nothing live makes no avatar call`() =
        runTest {
            twitch.on("helix/streams/followed", 200, streamsJson())
            assertThat(helix.followedStreams(TEST_CLIENT_ID, "tok", "42")).isEmpty()
            assertThat(twitch.requests).hasSize(1)
        }

    @Test
    fun `followedStreams still returns streams when the avatar call fails`() =
        runTest {
            twitch.on("helix/streams/followed", 200, streamsJson(streamJson("s1", "u1", "one")))
            twitch.on("helix/users", FakeTwitch.Reply(failure = IOException("boom")))

            val streams = helix.followedStreams(TEST_CLIENT_ID, "tok", "42")

            assertThat(streams.single().avatarUrl).isNull()
        }

    @Test
    fun `followedStreams maps 401 and other failures`() =
        runTest {
            twitch.on("helix/streams/followed", 401, "")
            assertThrows(UnauthorizedException::class.java) {
                kotlinx.coroutines.runBlocking { helix.followedStreams(TEST_CLIENT_ID, "tok", "42") }
            }

            val failing = FakeTwitch().apply { on("helix/streams/followed", 500, "not json") }
            val error =
                assertThrows(IdentityException::class.java) {
                    kotlinx.coroutines.runBlocking { failing.helix.followedStreams(TEST_CLIENT_ID, "tok", "42") }
                }
            assertThat(error).hasMessageThat().isEqualTo("Could not load followed streams")
        }

    @Test
    fun `userAvatars drops blanks and duplicates and skips users without a picture`() =
        runTest {
            twitch.on("helix/users", 200, usersJson(userJson("u1", "one", avatar = "a.png"), userJson("u2", "two")))

            val avatars = helix.userAvatars(TEST_CLIENT_ID, "tok", listOf("u1", "", "u1", "u2"))

            assertThat(avatars).containsExactly("u1", "a.png")
            assertThat(
                twitch.requests
                    .single()
                    .url
                    .substringAfter("?"),
            ).isEqualTo("id=u1&id=u2")
        }

    @Test
    fun `userAvatars with no usable ids makes no request`() =
        runTest {
            assertThat(helix.userAvatars(TEST_CLIENT_ID, "tok", listOf("", " "))).isEmpty()
            assertThat(twitch.requests).isEmpty()
        }

    @Test
    fun `userAvatars caps the batch at 100 ids`() =
        runTest {
            twitch.on("helix/users", 200, usersJson())
            helix.userAvatars(TEST_CLIENT_ID, "tok", (1..150).map { "u$it" })
            assertThat(
                twitch.requests
                    .single()
                    .url
                    .split("id=")
                    .size - 1,
            ).isEqualTo(100)
        }

    @Test
    fun `authorizedGet returns the body and sends the user token`() =
        runTest {
            twitch.on("chat/emotes", 200, """{"data":[]}""")

            val body = helix.authorizedGet("https://api.twitch.tv/helix/chat/emotes/global", TEST_CLIENT_ID, "tok", "Could not load emotes")

            assertThat(body).isEqualTo("""{"data":[]}""")
            assertThat(twitch.requests.single().headers["Authorization"]).isEqualTo("Bearer tok")
        }

    @Test
    fun `authorizedGet maps 401, non-200 and an empty body`() =
        runTest {
            val url = "https://api.twitch.tv/helix/chat/emotes/global"
            twitch.on("chat/emotes", 401, "")
            assertThrows(UnauthorizedException::class.java) {
                kotlinx.coroutines.runBlocking { helix.authorizedGet(url, TEST_CLIENT_ID, "tok", "Could not load emotes") }
            }

            val failing = FakeTwitch().apply { on("chat/emotes", 429, """{"message":"slow down"}""") }
            assertThat(
                assertThrows(IdentityException::class.java) {
                    kotlinx.coroutines.runBlocking { failing.helix.authorizedGet(url, TEST_CLIENT_ID, "tok", "Could not load emotes") }
                },
            ).hasMessageThat().isEqualTo("slow down")

            val noMessage = FakeTwitch().apply { on("chat/emotes", 500, "") }
            assertThat(
                assertThrows(IdentityException::class.java) {
                    kotlinx.coroutines.runBlocking { noMessage.helix.authorizedGet(url, TEST_CLIENT_ID, "tok", "Could not load emotes") }
                },
            ).hasMessageThat().isEqualTo("Could not load emotes")

            val empty = FakeTwitch().apply { on("chat/emotes", 200, "  ") }
            assertThat(
                assertThrows(IdentityException::class.java) {
                    kotlinx.coroutines.runBlocking { empty.helix.authorizedGet(url, TEST_CLIENT_ID, "tok", "Could not load emotes") }
                },
            ).hasMessageThat().isEqualTo("empty response")
        }

    @Test
    fun `a transport failure propagates as an IOException`() =
        runTest {
            twitch.on("helix/users", FakeTwitch.Reply(failure = IOException("offline")))
            val error = assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { helix.currentUser(TEST_CLIENT_ID, "t") } }
            assertThat(error).hasMessageThat().isEqualTo("offline")
        }

    private fun runBlockingCurrentUser() = twitch.currentUserBlocking()

    private fun FakeTwitch.currentUserBlocking() = kotlinx.coroutines.runBlocking { helix.currentUser(TEST_CLIENT_ID, "tok") }
}
