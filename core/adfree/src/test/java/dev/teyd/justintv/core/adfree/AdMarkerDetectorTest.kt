package dev.teyd.justintv.core.adfree

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdMarkerDetectorTest {
    /** Real media playlist head: Twitch session markers but no ads. */
    private val cleanPlaylist =
        """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:12364
        #EXT-X-DATERANGE:ID="playlist-creation-1791160100",CLASS="timestamp",START-DATE="2026-10-05T00:28:20.705Z",END-ON-NEXT=YES,X-SERVER-TIME="1791160100.70"
        #EXT-X-DATERANGE:ID="playlist-session-1791160100",CLASS="twitch-session",START-DATE="2026-10-05T00:28:20.705Z",END-ON-NEXT=YES,X-TV-TWITCH-SESSIONID="7856281238696364720"
        #EXT-X-DATERANGE:ID="source-1791160071",CLASS="twitch-stream-source",START-DATE="2026-10-05T00:27:51.454Z",END-ON-NEXT=YES,X-TV-TWITCH-STREAM-SOURCE="live"
        #EXTINF:2.000,live
        https://2e7a9c4a728b.j.cloudfront.hls.ttvnw.net/v1/segment/abc123.ts
        """.trimIndent()

    @Test
    fun `clean playlist has no ad markers`() {
        assertThat(AdMarkerDetector.hasAdMarkers(cleanPlaylist)).isFalse()
    }

    @Test
    fun `detects a stitched ad date range by id`() {
        val playlist =
            cleanPlaylist +
                """
                
                #EXT-X-DATERANGE:ID="stitched-ad-1791160300",CLASS="twitch-stitched-ad",START-DATE="2026-10-05T00:31:40.000Z",DURATION=30.0
                """.trimIndent()

        assertThat(AdMarkerDetector.hasAdMarkers(playlist)).isTrue()
    }

    @Test
    fun `detects a stitched ad date range by class`() {
        val playlist =
            cleanPlaylist +
                """
                
                #EXT-X-DATERANGE:ID="some-other-id",CLASS="twitch-stitched-ad",START-DATE="2026-10-05T00:31:40.000Z",DURATION=30.0
                """.trimIndent()

        assertThat(AdMarkerDetector.hasAdMarkers(playlist)).isTrue()
    }

    @Test
    fun `detects a stitched ad date range by attribute prefix`() {
        val playlist =
            cleanPlaylist +
                """
                
                #EXT-X-DATERANGE:ID="unlabelled",START-DATE="2026-10-05T00:31:40.000Z",X-TV-TWITCH-AD-ROLL-TYPE="midroll"
                """.trimIndent()

        assertThat(AdMarkerDetector.hasAdMarkers(playlist)).isTrue()
    }

    @Test
    fun `detects an Amazon titled segment`() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.000,Amazon
            https://edge.ads.example/segment.ts
            """.trimIndent()

        assertThat(AdMarkerDetector.hasAdMarkers(playlist)).isTrue()
    }

    @Test
    fun `ignores an ad title that is not a real ad server`() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.000,AmazonForestTour
            https://video.example/segment.ts
            #EXTINF:6.000,live
            https://video.example/segment2.ts
            """.trimIndent()

        assertThat(AdMarkerDetector.hasAdMarkers(playlist)).isFalse()
    }

    @Test
    fun `parses attributes that contain commas inside quotes`() {
        val line =
            """ID="trigger-1",CLASS="twitch-trigger",X-TV-TWITCH-TRIGGER-URL="https://euc11.playlist.ttvnw.net/trigger/abc,def",END-ON-NEXT=YES"""

        val attributes = AdMarkerDetector.parseAttributes(line)

        assertThat(attributes["ID"]).isEqualTo("trigger-1")
        assertThat(attributes["CLASS"]).isEqualTo("twitch-trigger")
        assertThat(attributes["X-TV-TWITCH-TRIGGER-URL"])
            .isEqualTo("https://euc11.playlist.ttvnw.net/trigger/abc,def")
        assertThat(attributes["END-ON-NEXT"]).isEqualTo("YES")
    }

    @Test
    fun `extracts the segment title`() {
        assertThat(AdMarkerDetector.segmentTitle("#EXTINF:2.000,live")).isEqualTo("live")
        assertThat(AdMarkerDetector.segmentTitle("#EXTINF:6.000,Amazon")).isEqualTo("Amazon")
        assertThat(AdMarkerDetector.segmentTitle("#EXTINF:2.000,")).isEmpty()
    }
}
