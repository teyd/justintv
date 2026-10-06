package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class TwitchIdentityApiTest {
    private val twitch = FakeTwitch()
    private val api = twitch.api

    @Test
    fun `validate returns the user, null for a rejected or incomplete token, and throws otherwise`() =
        runTest {
            twitch.on("oauth2/validate", 200, """{"login":"abc","user_id":"9","client_id":"x"}""")
            assertThat(api.validate("tok")).isEqualTo(TwitchUser("9", "abc", "abc"))
            assertThat(twitch.requests.single().headers["Authorization"]).isEqualTo("OAuth tok")
            assertThat(twitch.requests.single().headers).doesNotContainKey("Client-Id")

            val rejected = FakeTwitch().apply { on("oauth2/validate", 401, """{"message":"invalid access token"}""") }
            assertThat(rejected.api.validate("tok")).isNull()

            val noUser = FakeTwitch().apply { on("oauth2/validate", 200, """{"client_id":"x"}""") }
            assertThat(noUser.api.validate("tok")).isNull()

            val down = FakeTwitch().apply { on("oauth2/validate", 502, "") }
            assertThrows(IdentityException::class.java) { kotlinx.coroutines.runBlocking { down.api.validate("tok") } }
        }

    @Test
    fun `refresh returns the new grant`() =
        runTest {
            twitch.on("oauth2/token", 200, tokenJson("new", "newer", 3600, "user:read:follows chat:edit"))

            val grant = api.refresh(TEST_CLIENT_ID, "old-refresh")

            assertThat(grant)
                .isEqualTo(TokenGrant("new", "newer", 3600, listOf("user:read:follows", "chat:edit")))
            val sent = twitch.requests.single()
            assertThat(sent.method).isEqualTo("POST")
            assertThat(sent.body).contains("grant_type=refresh_token")
            assertThat(sent.body).contains("refresh_token=old-refresh")
            assertThat(sent.body).contains("client_id=$TEST_CLIENT_ID")
        }

    @Test
    fun `refresh throws Unauthorized only when Twitch rejects the refresh token`() =
        runTest {
            for ((code, body) in listOf(401 to "{}", 400 to """{"message":"Invalid refresh token"}""")) {
                val rejected = FakeTwitch().apply { on("oauth2/token", code, body) }
                assertThrows(UnauthorizedException::class.java) {
                    kotlinx.coroutines.runBlocking { rejected.api.refresh(TEST_CLIENT_ID, "r") }
                }
            }
            for ((code, body) in listOf(500 to "", 400 to """{"message":"missing client_id"}""")) {
                val other = FakeTwitch().apply { on("oauth2/token", code, body) }
                val error =
                    assertThrows(IdentityException::class.java) {
                        kotlinx.coroutines.runBlocking { other.api.refresh(TEST_CLIENT_ID, "r") }
                    }
                assertThat(error).isNotInstanceOf(UnauthorizedException::class.java)
            }
        }

    @Test
    fun `requestDeviceCode parses the code and applies defaults and minimums`() =
        runTest {
            twitch.on(
                "oauth2/device",
                200,
                """{"device_code":"d","user_code":"U-C","expires_in":0,"interval":0}""",
            )

            val code = api.requestDeviceCode(TEST_CLIENT_ID, "user:read:follows")

            assertThat(code.deviceCode).isEqualTo("d")
            assertThat(code.userCode).isEqualTo("U-C")
            assertThat(code.verificationUri).isEqualTo("https://www.twitch.tv/activate")
            assertThat(code.expiresInSeconds).isEqualTo(1)
            assertThat(code.intervalSeconds).isEqualTo(1)
            assertThat(twitch.requests.single().body).contains("scopes=user%3Aread%3Afollows")
        }

    @Test
    fun `requestDeviceCode surfaces Twitch's message when refused`() =
        runTest {
            twitch.on("oauth2/device", 400, """{"message":"invalid client"}""")
            val error =
                assertThrows(IdentityException::class.java) {
                    kotlinx.coroutines.runBlocking { api.requestDeviceCode(TEST_CLIENT_ID, "s") }
                }
            assertThat(error).hasMessageThat().isEqualTo("invalid client")
        }

    @Test
    fun `pollDeviceCode posts the device grant and parses the answer`() =
        runTest {
            twitch.on(
                "oauth2/token",
                FakeTwitch.Reply(400, """{"message":"authorization_pending"}"""),
                FakeTwitch.Reply(200, tokenJson("a", "r")),
            )

            assertThat(api.pollDeviceCode(TEST_CLIENT_ID, "s", "dev")).isEqualTo(DevicePoll.Pending)
            val granted = api.pollDeviceCode(TEST_CLIENT_ID, "s", "dev") as DevicePoll.Granted

            assertThat(granted.grant.accessToken).isEqualTo("a")
            assertThat(twitch.requests.first().body).contains("device_code=dev")
            assertThat(twitch.requests.first().body).contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
        }
}
