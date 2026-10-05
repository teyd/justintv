package dev.teyd.justintv.core.model

import java.time.Duration
import java.time.Instant
import java.util.Locale

/** 1234 -> "1.2K", 15000 -> "15K", 1200000 -> "1.2M". Twitch-style compact viewer counts. */
fun formatViewers(count: Int): String =
    when {
        count >= 1_000_000 -> compact(count / 1_000_000.0, "M")
        count >= 1_000 -> compact(count / 1_000.0, "K")
        else -> count.toString()
    }

/**
 * How long a stream has been live, from an ISO-8601 start time: "33min", "2h 33min", "1d 4h".
 * Null if the time is missing or cannot be read.
 */
fun formatUptime(
    startedAt: String?,
    now: Instant = Instant.now(),
): String? {
    if (startedAt.isNullOrBlank()) return null
    val start =
        try {
            Instant.parse(startedAt)
        } catch (_: Exception) {
            return null
        }
    val totalMinutes = Duration.between(start, now).toMinutes().coerceAtLeast(0)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
        totalMinutes >= 60 -> if (minutes > 0) "${totalMinutes / 60}h ${minutes}min" else "${totalMinutes / 60}h"
        else -> "${minutes}min"
    }
}

private fun compact(
    value: Double,
    suffix: String,
): String {
    val text =
        if (value >= 10) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.1f", value).removeSuffix(".0")
        }
    return text + suffix
}
