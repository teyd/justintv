package dev.teyd.justintv.core.network

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChannelPresence
import org.junit.Assert.assertThrows
import org.junit.Test

class DirectoryParserTest {
    /** Trimmed from a real anonymous `streams` response. */
    private val streamsBody =
        """
        {"data":{"streams":{"edges":[
          {"cursor":"abc","node":{"id":"317931542500","title":"DEMACIA CUP NAVI VS FLY","viewersCount":51779,
            "previewImageURL":"https://static-cdn.jtvnw.net/previews-ttv/live_user_caedrel-440x248.jpg",
            "broadcaster":{"login":"caedrel","displayName":"Caedrel",
              "profileImageURL":"https://static-cdn.jtvnw.net/jtv_user_pictures/x-70x70.png",
              "broadcastSettings":{"language":"EN"}},
            "game":{"name":"League of Legends","displayName":"League of Legends"}}},
          {"cursor":"def","node":{"id":"2","title":null,"viewersCount":10,"previewImageURL":null,
            "broadcaster":{"login":"quietone","displayName":null,"profileImageURL":null,"broadcastSettings":null},
            "game":null}}
        ]}}}
        """.trimIndent()

    @Test
    fun `a live channel gives viewers and start time`() {
        val live =
            DirectoryParser.parseChannelLive(
                """{"data":{"user":{"stream":{"viewersCount":50967,"createdAt":"2026-10-05T08:37:24Z"}}}}""",
            )
        assertThat(live).isEqualTo(ChannelLive(50967, "2026-10-05T08:37:24Z"))
    }

    @Test
    fun `an offline channel gives nothing`() {
        assertThat(DirectoryParser.parseChannelLive("""{"data":{"user":{"stream":null}}}""")).isNull()
        assertThat(DirectoryParser.parseChannelLive("""{"data":{"user":null}}""")).isNull()
    }

    @Test
    fun `parses live streams`() {
        val streams = DirectoryParser.parseTopStreams(streamsBody)

        assertThat(streams).hasSize(2)
        with(streams[0]) {
            assertThat(id).isEqualTo("317931542500")
            assertThat(login).isEqualTo("caedrel")
            assertThat(displayName).isEqualTo("Caedrel")
            assertThat(title).isEqualTo("DEMACIA CUP NAVI VS FLY")
            assertThat(viewerCount).isEqualTo(51779)
            assertThat(gameName).isEqualTo("League of Legends")
            assertThat(language).isEqualTo("EN")
            assertThat(previewUrl).contains("live_user_caedrel")
        }
    }

    @Test
    fun `tolerates missing optional fields`() {
        val quiet = DirectoryParser.parseTopStreams(streamsBody)[1]

        assertThat(quiet.login).isEqualTo("quietone")
        assertThat(quiet.displayName).isEqualTo("quietone") // falls back to the login
        assertThat(quiet.title).isEmpty()
        assertThat(quiet.gameName).isNull()
        assertThat(quiet.language).isNull()
        assertThat(quiet.previewUrl).isNull()
    }

    @Test
    fun `parses streams of a single game`() {
        val body =
            """
            {"data":{"game":{"streams":{"edges":[
              {"node":{"id":"9","title":"Chatting","viewersCount":5,
                "broadcaster":{"login":"dracon","broadcastSettings":{"language":"DE"}}}}
            ]}}}}
            """.trimIndent()

        val streams = DirectoryParser.parseGameStreams(body)

        assertThat(streams.map { it.login }).containsExactly("dracon")
        assertThat(streams.single().language).isEqualTo("DE")
    }

    @Test
    fun `an unknown game yields an empty list rather than an error`() {
        val streams = DirectoryParser.parseGameStreams("""{"data":{"game":null}}""")

        assertThat(streams).isEmpty()
    }

    @Test
    fun `parses games with box art and viewers`() {
        val body =
            """
            {"data":{"games":{"edges":[
              {"node":{"id":"509658","name":"Just Chatting","displayName":"Just Chatting",
                "boxArtURL":"https://static-cdn.jtvnw.net/ttv-boxart/509658-285x380.jpg","viewersCount":162687}},
              {"node":{"id":"1","name":null,"displayName":null,"boxArtURL":null,"viewersCount":null}}
            ]}}}
            """.trimIndent()

        val games = DirectoryParser.parseTopGames(body)

        // The entry without a name cannot be opened, so it is dropped.
        assertThat(games).hasSize(1)
        assertThat(games.single().id).isEqualTo("509658")
        assertThat(games.single().name).isEqualTo("Just Chatting")
        assertThat(games.single().viewerCount).isEqualTo(162687)
        assertThat(games.single().boxArtUrl).contains("509658")
    }

    @Test
    fun `channel search keeps live titles and marks a null stream offline`() {
        val body =
            """
            {"data":{"searchFor":{"channels":{"edges":[
              {"item":{"__typename":"User","id":"1","login":"shrood","displayName":"shrood",
                "profileImageURL":"https://static-cdn.jtvnw.net/shrood-70x70.png",
                "stream":{"id":"9","title":"24/7 VODS","viewersCount":116}}},
              {"item":{"__typename":"User","id":"2","login":"shroud","displayName":"shroud",
                "profileImageURL":"https://static-cdn.jtvnw.net/shroud-70x70.png","stream":null}},
              {"item":{"__typename":"Game","id":"3"}},
              {"item":{"__typename":"User","id":"1","login":"shrood","displayName":"shrood","stream":null}}
            ]}}}}
            """.trimIndent()

        val hits = DirectoryParser.parseChannelSearch(body)

        assertThat(hits.map { it.login }).containsExactly("shrood", "shroud").inOrder()
        val live = hits[0]
        assertThat(live.avatarUrl).contains("shrood")
        assertThat(live.presence).isEqualTo(ChannelPresence.Live("24/7 VODS", 116))
        assertThat(hits[1].presence).isEqualTo(ChannelPresence.Offline)
    }

    @Test
    fun `a live channel with no title stays live rather than looking offline`() {
        val body =
            """
            {"data":{"searchFor":{"channels":{"edges":[
              {"item":{"__typename":"User","login":"quiet","stream":{"viewersCount":0,"title":null}}}
            ]}}}}
            """.trimIndent()

        val hit = DirectoryParser.parseChannelSearch(body).single()

        assertThat(hit.presence).isEqualTo(ChannelPresence.Live("", 0))
        assertThat(hit.displayName).isEqualTo("quiet")
    }

    @Test
    fun `an integrity failure becomes a message about logging in`() {
        val body = """{"errors":[{"message":"failed integrity check"}],"data":{"streams":null}}"""

        val error =
            assertThrows(DirectoryException::class.java) {
                DirectoryParser.parseTopStreams(body)
            }

        assertThat(error).hasMessageThat().contains("Log in")
    }

    @Test
    fun `other graphql errors surface their message`() {
        val body = """{"errors":[{"message":"argument 'first' value must be between 1 and 30."}]}"""

        val error =
            assertThrows(DirectoryException::class.java) {
                DirectoryParser.parseTopStreams(body)
            }

        assertThat(error).hasMessageThat().contains("between 1 and 30")
    }

    @Test
    fun `an html error page is reported as unreadable`() {
        val error =
            assertThrows(DirectoryException::class.java) {
                DirectoryParser.parseTopGames("<html>bad gateway</html>")
            }

        assertThat(error).hasMessageThat().contains("Unreadable")
    }
}
