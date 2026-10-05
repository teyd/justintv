package dev.teyd.justintv.feature.watch

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.player.PlayerStats
import org.junit.Test

class StatsFormatTest {

    @Test
    fun `latency and buffer are shown in seconds with one decimal`() {
        assertThat(StatsFormat.seconds(3_240)).isEqualTo("3.2s")
        assertThat(StatsFormat.seconds(0)).isEqualTo("0.0s")
        assertThat(StatsFormat.seconds(12_960)).isEqualTo("13.0s")
    }

    @Test
    fun `unknown latency is a dash, not a bogus number`() {
        assertThat(StatsFormat.seconds(null)).isEqualTo("–")
        assertThat(StatsFormat.seconds(-1)).isEqualTo("–")
    }

    @Test
    fun `bitrates are in megabits`() {
        assertThat(StatsFormat.megabits(6_000_000)).isEqualTo("6.0 Mbps")
        assertThat(StatsFormat.megabits(24_130_000)).isEqualTo("24.1 Mbps")
        assertThat(StatsFormat.megabits(null)).isEqualTo("–")
        assertThat(StatsFormat.megabits(0)).isEqualTo("–")
    }

    @Test
    fun `resolution includes the frame rate when known`() {
        assertThat(StatsFormat.resolution(1920, 1080, 60f)).isEqualTo("1920×1080 60fps")
        assertThat(StatsFormat.resolution(1280, 720, null)).isEqualTo("1280×720")
        assertThat(StatsFormat.resolution(null, 720, 30f)).isEqualTo("–")
    }

    @Test
    fun `the pill shows latency and source`() {
        val stats = PlayerStats(liveLatencyMs = 3_200)

        assertThat(StatsFormat.pill(stats, "eu2.luminous.dev")).isEqualTo("3.2s · eu2.luminous.dev")
        assertThat(StatsFormat.pill(stats, "")).isEqualTo("3.2s")
    }

    @Test
    fun `stats panel lists every field in order`() {
        val lines = StatsFormat.lines(
            PlayerStats(
                liveLatencyMs = 3_200,
                bufferedMs = 4_100,
                width = 1920,
                height = 1080,
                frameRate = 60f,
                bitrate = 6_000_000,
                codecs = "avc1.64002A,mp4a.40.2",
                bandwidthBps = 24_100_000,
                droppedFrames = 12,
                playbackSpeed = 1.03f,
            ),
            source = "eu2.luminous.dev",
        ).toMap()

        assertThat(lines.keys).containsExactly(
            "latency", "buffer", "video", "bitrate", "network", "codec", "dropped", "speed", "source",
        ).inOrder()
        assertThat(lines["latency"]).isEqualTo("3.2s")
        assertThat(lines["buffer"]).isEqualTo("4.1s")
        assertThat(lines["video"]).isEqualTo("1920×1080 60fps")
        assertThat(lines["network"]).isEqualTo("24.1 Mbps")
        assertThat(lines["dropped"]).isEqualTo("12")
        assertThat(lines["speed"]).isEqualTo("1.03x")
        assertThat(lines["source"]).isEqualTo("eu2.luminous.dev")
    }

    @Test
    fun `an empty stats snapshot renders without crashing`() {
        val lines = StatsFormat.lines(PlayerStats(), source = "").toMap()

        assertThat(lines["latency"]).isEqualTo("–")
        assertThat(lines["codec"]).isEqualTo("–")
        assertThat(lines["source"]).isEqualTo("–")
    }
}
