package dev.teyd.justintv.feature.streams

import java.util.Locale

/** 1234 -> "1.2K", 15000 -> "15K", 1200000 -> "1.2M". Twitch-style compact viewer counts. */
fun formatViewers(count: Int): String = when {
    count >= 1_000_000 -> compact(count / 1_000_000.0, "M")
    count >= 1_000 -> compact(count / 1_000.0, "K")
    else -> count.toString()
}

private fun compact(value: Double, suffix: String): String {
    val text = if (value >= 10) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    }
    return text + suffix
}
