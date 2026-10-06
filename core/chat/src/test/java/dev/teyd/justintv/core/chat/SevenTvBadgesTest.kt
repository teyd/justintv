package dev.teyd.justintv.core.chat

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChatBadgeSource
import dev.teyd.justintv.core.network.JsonPoster
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SevenTvBadgesTest {
    // Trimmed from a real v4 GraphQL response captured on 2026-10-06.

    private val oneBadge =
        """
        {"data":{"users":{
          "u0":{"style":{"activeBadge":{
            "id":"01JJJ74CRHZBRMCM8F4Y2WBN6R","name":"Minecraft Event Winner",
            "description":"Minecraft Event Winner",
            "images":[
              {"url":"https://cdn.7tv.app/badge/x/1x_static.webp","mime":"image/webp","width":18,"height":18,"scale":1,"frameCount":1},
              {"url":"https://cdn.7tv.app/badge/x/3x_static.png","mime":"image/png","width":54,"height":54,"scale":3,"frameCount":1},
              {"url":"https://cdn.7tv.app/badge/x/4x.webp","mime":"image/webp","width":72,"height":72,"scale":4,"frameCount":100}
            ]}}},
          "u1":{"style":{"activeBadge":null}}
        }}}
        """.trimIndent()

    @Test
    fun `resolve queries unknown users with aliases and caches them`() =
        runTest {
            val queries = mutableListOf<String>()
            val badges =
                SevenTvBadges(
                    JsonPoster { _, body ->
                        queries += body
                        oneBadge
                    },
                )

            badges.resolve(listOf("42", "43"))

            assertThat(queries).hasSize(1)
            assertThat(queries.single()).contains("u0: userByConnection(platform: TWITCH, platformId: \"42\")")
            assertThat(queries.single()).contains("u1: userByConnection(platform: TWITCH, platformId: \"43\")")

            val badge = badges.cached("42")!!
            assertThat(badge.source).isEqualTo(ChatBadgeSource.SevenTv)
            // Largest static raster, not the animated 4x.
            assertThat(badge.url).isEqualTo("https://cdn.7tv.app/badge/x/3x_static.png")
            assertThat(badge.title).isEqualTo("Minecraft Event Winner")
            // u1 has no active badge, and both are known now.
            assertThat(badges.cached("43")).isNull()
            assertThat(badges.isKnown("43")).isTrue()

            badges.resolve(listOf("42", "43"))
            assertThat(queries).hasSize(1)
        }

    @Test
    fun `known users are not looked up again in a later batch`() =
        runTest {
            val queries = mutableListOf<String>()
            val badges =
                SevenTvBadges(
                    JsonPoster { _, body ->
                        queries += body
                        oneBadge
                    },
                )

            badges.resolve(listOf("42", "43"))
            badges.resolve(listOf("44"))

            assertThat(queries).hasSize(2)
            assertThat(queries.last()).doesNotContain("platformId: \"42\"")
            assertThat(queries.last()).contains("platformId: \"44\"")
        }

    @Test
    fun `lookups are split into batches`() =
        runTest {
            val queries = mutableListOf<String>()
            val badges =
                SevenTvBadges(
                    poster =
                        JsonPoster { _, body ->
                            queries += body
                            """{"data":{"users":{}}}"""
                        },
                    batchSize = 2,
                )

            badges.resolve(listOf("1", "2", "3", "4", "5"))

            assertThat(queries).hasSize(3)
            assertThat(queries[0]).contains("u1:")
            assertThat(queries[0]).doesNotContain("u2:")
            assertThat(queries[2]).contains("u0:")
            assertThat(queries[2]).doesNotContain("u1:")
        }

    @Test
    fun `a failed request caches nothing so the next message retries`() =
        runTest {
            var calls = 0
            val badges =
                SevenTvBadges(
                    JsonPoster { _, _ ->
                        calls++
                        throw IllegalStateException("7TV is down")
                    },
                )

            assertThat(runCatching { badges.resolve(listOf("42")) }.exceptionOrNull())
                .isInstanceOf(IllegalStateException::class.java)
            assertThat(badges.isKnown("42")).isFalse()

            assertThat(runCatching { badges.resolve(listOf("42")) }.exceptionOrNull())
                .isInstanceOf(IllegalStateException::class.java)
            assertThat(calls).isEqualTo(2)
        }

    @Test
    fun `a malformed body throws and caches nothing`() =
        runTest {
            val badges = SevenTvBadges(JsonPoster { _, _ -> "not json" })

            assertThat(runCatching { badges.resolve(listOf("42")) }.isFailure).isTrue()
            assertThat(badges.isKnown("42")).isFalse()
        }
}
