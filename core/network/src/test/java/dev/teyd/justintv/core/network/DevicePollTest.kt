package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DevicePollTest {
    @Test
    fun `a granted poll keeps the refresh token`() {
        val poll =
            TwitchIdentityApi.parseDevicePoll(
                200,
                """{"access_token":"abc","refresh_token":"def","expires_in":14820,"scope":["user:read:follows"]}""",
            )
        val granted = poll as DevicePoll.Granted
        assertThat(granted.grant.accessToken).isEqualTo("abc")
        assertThat(granted.grant.refreshToken).isEqualTo("def")
        assertThat(granted.grant.scopes).containsExactly("user:read:follows")
    }

    @Test
    fun `authorization pending is not a failure`() {
        val poll = TwitchIdentityApi.parseDevicePoll(400, """{"message":"authorization_pending"}""")
        assertThat(poll).isEqualTo(DevicePoll.Pending)
    }

    @Test
    fun `a dead refresh token is the only refresh failure that signs the user out`() {
        assertThat(TwitchIdentityApi.isRejectedRefresh(400, """{"message":"Invalid refresh token"}""")).isTrue()
        assertThat(TwitchIdentityApi.isRejectedRefresh(400, """{"error":"invalid_grant"}""")).isTrue()
        assertThat(TwitchIdentityApi.isRejectedRefresh(401, """{"message":"invalid token"}""")).isTrue()
        assertThat(TwitchIdentityApi.isRejectedRefresh(400, """{"message":"parameter client_id is required"}""")).isFalse()
        assertThat(TwitchIdentityApi.isRejectedRefresh(500, """{"message":"Invalid refresh token"}""")).isFalse()
    }

    @Test
    fun `slow down is not a failure`() {
        val poll = TwitchIdentityApi.parseDevicePoll(400, """{"message":"slow_down"}""")
        assertThat(poll).isEqualTo(DevicePoll.SlowDown)
    }

    @Test
    fun `a denied poll surfaces the message`() {
        val poll = TwitchIdentityApi.parseDevicePoll(400, """{"message":"The user denied access"}""")
        assertThat((poll as DevicePoll.Rejected).message).isEqualTo("The user denied access")
    }
}
