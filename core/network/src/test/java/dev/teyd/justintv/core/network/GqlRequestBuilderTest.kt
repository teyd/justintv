package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class GqlRequestBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `persisted request carries the operation, hash and variables`() {
        val body = GqlRequestBuilder.persistedAccessTokenRequest("Dona", PlayerTypes.POPOUT)

        val root = json.parseToJsonElement(body).jsonObject
        assertThat(root["operationName"]?.jsonPrimitive?.content).isEqualTo("PlaybackAccessToken")

        val hash =
            root["extensions"]
                ?.jsonObject
                ?.get("persistedQuery")
                ?.jsonObject
                ?.get("sha256Hash")
                ?.jsonPrimitive
                ?.content
        assertThat(hash).isEqualTo(GqlRequestBuilder.ACCESS_TOKEN_QUERY_HASH)

        val variables = root["variables"]!!.jsonObject
        assertThat(variables["login"]?.jsonPrimitive?.content).isEqualTo("dona")
        assertThat(variables["isLive"]?.jsonPrimitive?.content).isEqualTo("true")
        assertThat(variables["isVod"]?.jsonPrimitive?.content).isEqualTo("false")
        assertThat(variables["playerType"]?.jsonPrimitive?.content).isEqualTo("popout")
    }

    @Test
    fun `full request embeds the query text as a fallback`() {
        val body = GqlRequestBuilder.fullAccessTokenRequest("dona", PlayerTypes.SITE)

        val root = json.parseToJsonElement(body).jsonObject
        val query = root["query"]?.jsonPrimitive?.content.orEmpty()
        assertThat(query).contains("streamPlaybackAccessToken")
        assertThat(query).contains("value")
        assertThat(query).contains("signature")
    }
}
