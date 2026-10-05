package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DirectoryQueriesTest {
    @Test
    fun `top streams without a filter has no options`() {
        val query = DirectoryQueries.topStreams(emptySet())

        assertThat(query).startsWith("query{streams(first:30)")
        assertThat(query).doesNotContain("options")
    }

    @Test
    fun `top streams puts languages in as enum values`() {
        val query = DirectoryQueries.topStreams(setOf("DE", "EN"))

        // Stable order from the language list, not the set order.
        assertThat(query).contains("options:{languages:[EN,DE]}")
    }

    @Test
    fun `unknown language codes are dropped rather than sent to Twitch`() {
        val query = DirectoryQueries.topStreams(setOf("EN", "XX", "PT_BR", "x]}; drop"))

        assertThat(query).contains("languages:[EN]")
        assertThat(query).doesNotContain("XX")
        assertThat(query).doesNotContain("drop")
    }

    @Test
    fun `an all-invalid filter behaves like no filter`() {
        val query = DirectoryQueries.topStreams(setOf("XX"))

        assertThat(query).doesNotContain("options")
    }

    @Test
    fun `stream count is capped at what anonymous graphql allows`() {
        assertThat(DirectoryQueries.topStreams(emptySet(), limit = 500)).contains("first:30")
        assertThat(DirectoryQueries.topStreams(emptySet(), limit = 0)).contains("first:1")
    }

    @Test
    fun `game streams use quoted language strings`() {
        val query = DirectoryQueries.gameStreams("Just Chatting", setOf("DE", "FR"))

        assertThat(query).contains("game(name:\"Just Chatting\")")
        assertThat(query).contains("options:{languages:[\"DE\",\"FR\"]}")
    }

    @Test
    fun `game names are escaped so they cannot break out of the query`() {
        val query = DirectoryQueries.gameStreams("Say \"hi\"}) { evil", emptySet())

        assertThat(query).contains("game(name:\"Say \\\"hi\\\"}) { evil\")")
    }

    @Test
    fun `channel search escapes the query and asks for live and offline fields`() {
        val query = DirectoryQueries.searchChannels("Say \"hi\"}) { evil")

        assertThat(query).contains("searchFor(userQuery:\"Say \\\"hi\\\"}) { evil\"")
        assertThat(query).contains("platform:\"web\"")
        assertThat(query).contains("profileImageURL")
        assertThat(query).contains("viewersCount")
        assertThat(query).contains("stream{id title viewersCount}")
    }

    @Test
    fun `top games asks for the most watched first`() {
        val query = DirectoryQueries.topGames()

        assertThat(query).contains("first:100")
        assertThat(query).contains("sort:VIEWER_COUNT")
        assertThat(query).contains("boxArtURL")
    }
}
