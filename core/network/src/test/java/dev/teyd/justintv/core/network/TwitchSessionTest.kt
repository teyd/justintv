package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchSessionTest {
    private val twitch = FakeTwitch()
    private val streams = streamsJson(streamJson("s1", "u1", "one"))

    private fun TestScope.session(
        vault: FakeVault,
        clientId: String = TEST_CLIENT_ID,
    ) = TwitchSession(twitch.api, twitch.helix, vault, clientId, sessionScope()).also { runCurrent() }

    /** Not backgroundScope: advanceUntilIdle ignores background tasks, and the poll runs on virtual time. */
    private fun TestScope.sessionScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    private fun scriptHelix(
        users: FakeTwitch.Reply = FakeTwitch.Reply(200, usersJson(userJson())),
        followed: FakeTwitch.Reply = FakeTwitch.Reply(200, streams),
    ) {
        twitch.on("helix/streams/followed", followed)
        twitch.on("helix/users", users)
    }

    private fun loggedIn(canChat: Boolean = false) = AuthState.LoggedIn("42", "viewer", "Viewer", canChat)

    // restore

    @Test
    fun `an empty vault starts logged out without touching the network`() =
        runTest {
            val session = session(FakeVault())
            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(session.accessToken()).isNull()
            assertThat(twitch.requests).isEmpty()
        }

    @Test
    fun `restore shows the saved account and refreshes the profile`() =
        runTest {
            scriptHelix(users = FakeTwitch.Reply(200, usersJson(userJson(displayName = "Renamed"))))
            val vault = FakeVault(storedSession())

            val session = session(vault)

            assertThat(session.state.value).isEqualTo(AuthState.LoggedIn("42", "viewer", "Renamed", false))
            assertThat(vault.session?.displayName).isEqualTo("Renamed")
            assertThat(vault.session?.accessToken).isEqualTo("access-1")
            assertThat(vault.session?.refreshToken).isEqualTo("refresh-1")
        }

    @Test
    fun `restore with a blank client id keeps the account and makes no requests`() =
        runTest {
            val vault = FakeVault(storedSession(expiresInMs = -1_000L))

            val session = session(vault, clientId = "")

            assertThat(session.state.value).isEqualTo(loggedIn())
            assertThat(vault.clears).isEqualTo(0)
            assertThat(twitch.requests).isEmpty()
            assertThat(session.accessToken()).isEqualTo("access-1")
        }

    @Test
    fun `restore keeps the account when the profile call fails on the network`() =
        runTest {
            scriptHelix(users = FakeTwitch.Reply(failure = IOException("offline")))
            val vault = FakeVault(storedSession())

            val session = session(vault)

            assertThat(session.state.value).isEqualTo(loggedIn())
            assertThat(vault.clears).isEqualTo(0)
        }

    @Test
    fun `restore with an expired token and a rejected refresh signs out`() =
        runTest {
            twitch.on("oauth2/token", 400, """{"message":"Invalid refresh token"}""")
            val vault = FakeVault(storedSession(expiresInMs = -1_000L))

            val session = session(vault)

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
        }

    @Test
    fun `restore with an expired token and a 5xx refresh keeps the account`() =
        runTest {
            twitch.on("oauth2/token", 503, "")
            scriptHelix(users = FakeTwitch.Reply(401))
            val vault = FakeVault(storedSession(expiresInMs = -1_000L))

            val session = session(vault)

            assertThat(session.state.value).isEqualTo(loggedIn())
            assertThat(vault.session).isNotNull()
            assertThat(vault.clears).isEqualTo(0)
        }

    // followedStreams

    @Test
    fun `followedStreams returns the live channels using the saved token`() =
        runTest {
            scriptHelix()
            val session = session(FakeVault(storedSession()))

            val result = session.followedStreams()

            assertThat(result.map { it.login }).containsExactly("one")
            val call = twitch.requestsTo("streams/followed").single()
            assertThat(call.headers["Authorization"]).isEqualTo("Bearer access-1")
            assertThat(call.url).contains("user_id=42")
            assertThat(twitch.requestsTo("oauth2/token")).isEmpty()
        }

    @Test
    fun `followedStreams while logged out says not signed in`() =
        runTest {
            val session = session(FakeVault())
            val error = assertThrows(IdentityException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }
            assertThat(error).hasMessageThat().isEqualTo("Not signed in")
        }

    @Test
    fun `a token about to expire is refreshed first and the new one is saved`() =
        runTest {
            scriptHelix()
            twitch.on("oauth2/token", 200, tokenJson("access-2", refresh = "", expiresIn = 14_000))
            val vault = FakeVault(storedSession(expiresInMs = 30_000L, scopes = listOf("user:read:follows", "chat:edit")))
            val session = session(vault)

            session.followedStreams()

            assertThat(twitch.requestsTo("streams/followed").single().headers["Authorization"]).isEqualTo("Bearer access-2")
            assertThat(vault.session?.accessToken).isEqualTo("access-2")
            assertThat(vault.session?.refreshToken).isEqualTo("refresh-1")
            assertThat(vault.session?.scopes).containsExactly("user:read:follows", "chat:edit")
            assertThat(session.state.value).isEqualTo(loggedIn(canChat = true))
            assertThat(twitch.requestsTo("oauth2/token")).hasSize(1)
        }

    @Test
    fun `a rotated refresh token replaces the old one`() =
        runTest {
            scriptHelix()
            twitch.on("oauth2/token", 200, tokenJson("access-2", refresh = "refresh-2", scopes = "user:read:follows"))
            val vault = FakeVault(storedSession(expiresInMs = 30_000L))

            session(vault).followedStreams()

            assertThat(vault.session?.refreshToken).isEqualTo("refresh-2")
            assertThat(twitch.requestsTo("oauth2/token").first().body).contains("refresh_token=refresh-1")
        }

    @Test
    fun `a rejected refresh signs out and wipes the vault`() =
        runTest {
            scriptHelix()
            twitch.on(
                "oauth2/token",
                FakeTwitch.Reply(200, tokenJson("access-2", "refresh-2", expiresIn = 30)),
                FakeTwitch.Reply(400, """{"error":"invalid_grant"}"""),
            )
            val vault = FakeVault(storedSession(expiresInMs = 30_000L))
            val session = session(vault)
            assertThat(session.state.value).isEqualTo(loggedIn())

            val error = assertThrows(IdentityException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }

            assertThat(error).hasMessageThat().isEqualTo("Not signed in")
            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
            assertThat(vault.clears).isEqualTo(1)
        }

    @Test
    fun `a 401 from the token endpoint also signs out`() =
        runTest {
            twitch.on("oauth2/token", 401, "")
            val vault = FakeVault(storedSession(expiresInMs = 30_000L))

            val session = session(vault)

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
        }

    @Test
    fun `a 5xx or network failure on refresh keeps the account and uses the unexpired token`() =
        runTest {
            for (failure in listOf(FakeTwitch.Reply(502, ""), FakeTwitch.Reply(failure = IOException("offline")))) {
                val fresh = FakeTwitch()
                val vault = FakeVault(storedSession(expiresInMs = 30_000L))
                fresh.on("oauth2/token", failure)
                fresh.on("helix/streams/followed", 200, streams)
                fresh.on("helix/users", 200, usersJson(userJson()))
                val session = TwitchSession(fresh.api, fresh.helix, vault, TEST_CLIENT_ID, sessionScope())
                runCurrent()

                assertThat(session.followedStreams()).hasSize(1)

                assertThat(session.state.value).isEqualTo(loggedIn())
                assertThat(vault.clears).isEqualTo(0)
                assertThat(vault.session?.refreshToken).isEqualTo("refresh-1")
                assertThat(fresh.requestsTo("streams/followed").single().headers["Authorization"]).isEqualTo("Bearer access-1")
            }
        }

    @Test
    fun `an expired token with a failing refresh reports not signed in but keeps the saved account`() =
        runTest {
            twitch.on("oauth2/token", FakeTwitch.Reply(failure = IOException("offline")))
            scriptHelix(users = FakeTwitch.Reply(401))
            val vault = FakeVault(storedSession(expiresInMs = -1_000L))
            val session = session(vault)

            assertThrows(IdentityException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }

            assertThat(vault.session).isNotNull()
            assertThat(vault.clears).isEqualTo(0)
            assertThat(session.state.value).isEqualTo(loggedIn())
        }

    @Test
    fun `a blank client id never refreshes and never wipes the account`() =
        runTest {
            scriptHelix(followed = FakeTwitch.Reply(401))
            val vault = FakeVault(storedSession(expiresInMs = -1_000L))
            val session = session(vault, clientId = "")

            assertThrows(UnauthorizedException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }

            assertThat(twitch.requestsTo("oauth2/token")).isEmpty()
            assertThat(vault.clears).isEqualTo(0)
            assertThat(vault.session).isNotNull()
        }

    @Test
    fun `an expired token with no refresh token signs out`() =
        runTest {
            val vault = FakeVault(storedSession(expiresInMs = -1_000L, refreshToken = ""))
            val session = session(vault)

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
            assertThat(twitch.requestsTo("oauth2/token")).isEmpty()
        }

    @Test
    fun `a 401 on an unexpired token refreshes once and retries`() =
        runTest {
            twitch.on(
                "helix/streams/followed",
                FakeTwitch.Reply(401),
                FakeTwitch.Reply(200, streams),
            )
            twitch.on("helix/users", 200, usersJson(userJson()))
            twitch.on("oauth2/token", 200, tokenJson("access-2", "refresh-2"))
            val session = session(FakeVault(storedSession()))

            assertThat(session.followedStreams()).hasSize(1)

            assertThat(twitch.requestsTo("streams/followed").last().headers["Authorization"]).isEqualTo("Bearer access-2")
        }

    @Test
    fun `concurrent 401s on the same unexpired token refresh once`() =
        runTest {
            twitch.on(
                "helix/streams/followed",
                FakeTwitch.Reply(401),
                FakeTwitch.Reply(200, streams),
            )
            twitch.on("helix/users", 200, usersJson(userJson()))
            twitch.on("oauth2/token", 200, tokenJson("access-2", "refresh-2"))
            val session = session(FakeVault(storedSession()))

            val results = listOf(async { session.followedStreams() }, async { session.followedStreams() }).awaitAll()

            assertThat(results.map { it.size }).containsExactly(1, 1)
            assertThat(twitch.requestsTo("streams/followed").map { it.headers["Authorization"] })
                .containsExactly("Bearer access-1", "Bearer access-2", "Bearer access-2")
            assertThat(twitch.requestsTo("oauth2/token")).hasSize(1)
        }

    @Test
    fun `a 401 on an unexpired token whose refresh is rejected signs out`() =
        runTest {
            scriptHelix(followed = FakeTwitch.Reply(401))
            twitch.on("oauth2/token", 400, """{"error":"invalid_grant"}""")
            val vault = FakeVault(storedSession())
            val session = session(vault)

            assertThrows(IdentityException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
            assertThat(vault.clears).isEqualTo(1)
        }

    @Test
    fun `a 401 on an unexpired token with a 5xx refresh keeps the account`() =
        runTest {
            scriptHelix(followed = FakeTwitch.Reply(401))
            twitch.on("oauth2/token", 503, "")
            val vault = FakeVault(storedSession())
            val session = session(vault)

            assertThrows(UnauthorizedException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }

            assertThat(session.state.value).isEqualTo(loggedIn())
            assertThat(vault.session?.refreshToken).isEqualTo("refresh-1")
            assertThat(vault.clears).isEqualTo(0)
        }

    @Test
    fun `restore refreshes and retries when the profile call 401s on an unexpired token`() =
        runTest {
            twitch.on("helix/users", FakeTwitch.Reply(401), FakeTwitch.Reply(200, usersJson(userJson(displayName = "Renamed"))))
            twitch.on("oauth2/token", 200, tokenJson("access-2", "refresh-2"))
            val vault = FakeVault(storedSession())

            val session = session(vault)

            assertThat(twitch.requestsTo("helix/users").last().headers["Authorization"]).isEqualTo("Bearer access-2")
            assertThat(vault.session?.accessToken).isEqualTo("access-2")
            assertThat(vault.session?.displayName).isEqualTo("Renamed")
            assertThat(session.state.value).isEqualTo(AuthState.LoggedIn("42", "viewer", "Renamed", false))
        }

    @Test
    fun `restore signs out when the refresh after a profile 401 is rejected`() =
        runTest {
            twitch.on("helix/users", 401, "")
            twitch.on("oauth2/token", 400, """{"error":"invalid_grant"}""")
            val vault = FakeVault(storedSession())

            val session = session(vault)

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
        }

    @Test
    fun `a 401 that survives the retry surfaces as unauthorized`() =
        runTest {
            scriptHelix(followed = FakeTwitch.Reply(401))
            val session = session(FakeVault(storedSession()))

            assertThrows(UnauthorizedException::class.java) { kotlinx.coroutines.runBlocking { session.followedStreams() } }
        }

    // accessToken and logout

    @Test
    fun `accessToken returns the saved token while it is fresh`() =
        runTest {
            scriptHelix()
            val session = session(FakeVault(storedSession()))
            assertThat(session.accessToken()).isEqualTo("access-1")
        }

    @Test
    fun `logout clears the vault and the state`() =
        runTest {
            scriptHelix()
            val vault = FakeVault(storedSession())
            val session = session(vault)

            session.logout()
            advanceUntilIdle()

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(vault.session).isNull()
            assertThat(session.accessToken()).isNull()
        }

    // device sign-in

    private fun scriptDeviceCode() =
        twitch.on(
            "oauth2/device",
            200,
            """{"device_code":"dev","user_code":"ABCD","verification_uri":"https://t.tv/a","expires_in":1800,"interval":1}""",
        )

    @Test
    fun `sign-in shows the code, waits through pending and slow down, then saves the account`() =
        runTest {
            scriptDeviceCode()
            twitch.on(
                "oauth2/token",
                FakeTwitch.Reply(400, """{"message":"authorization_pending"}"""),
                FakeTwitch.Reply(400, """{"message":"slow_down"}"""),
                FakeTwitch.Reply(200, tokenJson("tok", "ref", 14_000, "user:read:follows")),
            )
            twitch.on("helix/users", 200, usersJson(userJson()))
            val vault = FakeVault()
            val session = session(vault)

            session.start()
            runCurrent()
            assertThat(session.state.value).isEqualTo(AuthState.Pending("ABCD", "https://t.tv/a"))
            advanceUntilIdle()

            assertThat(session.state.value).isEqualTo(loggedIn())
            assertThat(vault.session?.accessToken).isEqualTo("tok")
            assertThat(vault.session?.refreshToken).isEqualTo("ref")
            assertThat(vault.session?.scopes).containsExactly("user:read:follows")
            assertThat(twitch.requestsTo("oauth2/token")).hasSize(3)
        }

    @Test
    fun `grantChat requests the chat scopes and enables chat`() =
        runTest {
            scriptDeviceCode()
            twitch.on("oauth2/token", 200, tokenJson("tok", "ref", 14_000, "user:read:follows chat:read chat:edit"))
            twitch.on("helix/users", 200, usersJson(userJson()))
            val session = session(FakeVault())

            session.grantChat()
            advanceUntilIdle()

            assertThat(twitch.requestsTo("oauth2/device").single().body).contains("chat%3Aedit")
            assertThat(session.state.value).isEqualTo(loggedIn(canChat = true))
        }

    @Test
    fun `a denied sign-in fails when nobody was signed in`() =
        runTest {
            scriptDeviceCode()
            twitch.on("oauth2/token", 400, """{"message":"access_denied"}""")
            val session = session(FakeVault())

            session.start()
            advanceUntilIdle()

            assertThat(session.state.value).isEqualTo(AuthState.Failed("access_denied"))
        }

    @Test
    fun `a failed re-consent keeps the signed-in account`() =
        runTest {
            scriptHelix()
            scriptDeviceCode()
            twitch.on("oauth2/token", 400, """{"message":"access_denied"}""")
            val vault = FakeVault(storedSession())
            val session = session(vault)

            session.grantChat()
            advanceUntilIdle()

            assertThat(session.state.value).isEqualTo(loggedIn())
            assertThat(vault.session).isNotNull()
        }

    @Test
    fun `sign-in with a blank client id fails without a request`() =
        runTest {
            val session = session(FakeVault(), clientId = "")

            session.start()
            advanceUntilIdle()

            assertThat(session.state.value).isEqualTo(AuthState.Failed("This build has no Twitch client ID."))
            assertThat(twitch.requests).isEmpty()
        }

    @Test
    fun `sign-in retries DNS failures then reports a connection message`() =
        runTest {
            twitch.on("oauth2/device", FakeTwitch.Reply(failure = UnknownHostException("id.twitch.tv")))
            val session = session(FakeVault())

            session.start()
            advanceUntilIdle()

            assertThat(twitch.requestsTo("oauth2/device")).hasSize(3)
            assertThat((session.state.value as AuthState.Failed).message).startsWith("Couldn't reach Twitch")
        }

    @Test
    fun `sign-in does not retry other failures and shows their message`() =
        runTest {
            twitch.on("oauth2/device", 400, """{"message":"invalid client"}""")
            val session = session(FakeVault())

            session.start()
            advanceUntilIdle()

            assertThat(twitch.requestsTo("oauth2/device")).hasSize(1)
            assertThat(session.state.value).isEqualTo(AuthState.Failed("invalid client"))
        }

    @Test
    fun `logout during sign-in cancels the poll`() =
        runTest {
            scriptDeviceCode()
            twitch.on("oauth2/token", 400, """{"message":"authorization_pending"}""")
            val session = session(FakeVault())

            session.start()
            runCurrent()
            session.logout()
            advanceUntilIdle()

            assertThat(session.state.value).isEqualTo(AuthState.LoggedOut)
            assertThat(twitch.requestsTo("oauth2/token")).isEmpty()
        }
}
