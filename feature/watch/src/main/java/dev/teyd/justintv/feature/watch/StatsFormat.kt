package dev.teyd.justintv.feature.watch

import dev.teyd.justintv.core.player.PlayerStats
import java.util.Locale

/** Text for the stats overlay. Pure functions so the formatting is covered by unit tests. */
object StatsFormat {

    private const val UNKNOWN = "–"

    fun seconds(ms: Long?): String =
        if (ms == null || ms < 0) UNKNOWN else String.format(Locale.US, "%.1fs", ms / 1000.0)

    fun megabits(bitsPerSecond: Long?): String =
        if (bitsPerSecond == null || bitsPerSecond <= 0) {
            UNKNOWN
        } else {
            String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
        }

    fun resolution(width: Int?, height: Int?, frameRate: Float?): String {
        if (width == null || height == null) return UNKNOWN
        val fps = frameRate?.let { " ${Math.round(it)}fps" }.orEmpty()
        return "${width}×$height$fps"
    }

    /** Pill shown on the video at all times: live delay and where the stream comes from. */
    fun pill(stats: PlayerStats, source: String): String =
        if (source.isBlank()) seconds(stats.liveLatencyMs) else "${seconds(stats.liveLatencyMs)} · $source"

    /** Label and value pairs for the stats panel, in display order. */
    fun lines(stats: PlayerStats, source: String): List<Pair<String, String>> = listOf(
        "latency" to seconds(stats.liveLatencyMs),
        "buffer" to seconds(stats.bufferedMs),
        "video" to resolution(stats.width, stats.height, stats.frameRate),
        "bitrate" to megabits(stats.bitrate?.toLong()),
        "network" to megabits(stats.bandwidthBps),
        "codec" to (stats.codecs?.takeIf { it.isNotBlank() } ?: UNKNOWN),
        "dropped" to stats.droppedFrames.toString(),
        "speed" to String.format(Locale.US, "%.2fx", stats.playbackSpeed),
        "source" to source.ifBlank { UNKNOWN },
    )
}
