package dev.teyd.justintv.core.adfree

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistParserTest {
    private val masterPlaylist =
        """
        #EXTM3U
        #EXT-X-SESSION-DATA:DATA-ID="NODE",VALUE="2e7a9c4a728b.j.cloudfront.hls.ttvnw.net"
        #EXT-X-STREAM-INF:BANDWIDTH=630000,RESOLUTION=640x360,CODECS="avc1.4D401F,mp4a.40.2",STABLE-VARIANT-ID="360p30"
        https://video-edge.example/v1/playlist/360p.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",STABLE-VARIANT-ID="720p60"
        https://video-edge.example/v1/playlist/720p.m3u8
        """.trimIndent()

    @Test
    fun `finds the first variant playlist`() {
        val url = PlaylistParser.findMediaPlaylistUrl(masterPlaylist)

        assertThat(url).isEqualTo("https://video-edge.example/v1/playlist/360p.m3u8")
    }

    @Test
    fun `resolves a relative variant url against the playlist url`() {
        val relativeMaster =
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=630000
            ../playlist/360p.m3u8?foo=bar
            """.trimIndent()

        val url =
            PlaylistParser.findMediaPlaylistUrl(
                relativeMaster,
                baseUrl = "https://proxy.example/live/channel/index.m3u8",
            )

        assertThat(url).isEqualTo("https://proxy.example/live/playlist/360p.m3u8?foo=bar")
    }

    @Test
    fun `returns null for a media playlist`() {
        val media =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:2.000,live
            https://video-edge.example/segment.ts
            """.trimIndent()

        assertThat(PlaylistParser.findMediaPlaylistUrl(media)).isNull()
        assertThat(PlaylistParser.isMediaPlaylist(media)).isTrue()
    }

    @Test
    fun `recognises a master playlist`() {
        assertThat(PlaylistParser.isMediaPlaylist(masterPlaylist)).isFalse()
    }
}
