package dev.teyd.justintv.core.adfree

import java.net.URI

/** Reads variant and media playlist URLs out of HLS playlist text. */
object PlaylistParser {

    /**
     * Returns the first variant playlist URI in a master playlist, resolved against
     * [baseUrl] when it is relative. Returns null when the text is already a media playlist
     * or has no variants.
     */
    fun findMediaPlaylistUrl(playlistText: String, baseUrl: String? = null): String? {
        val lines = playlistText.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        lines.forEachIndexed { index, line ->
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val candidate = lines.getOrNull(index + 1) ?: return null
                if (!candidate.startsWith("#")) return resolve(baseUrl, candidate)
            }
        }
        return null
    }

    /** True when the text is a media playlist, which lists segments rather than variants. */
    fun isMediaPlaylist(playlistText: String): Boolean =
        playlistText.lineSequence().any { it.trim().startsWith("#EXTINF") }

    private fun resolve(baseUrl: String?, url: String): String {
        if (baseUrl == null) return url
        return try {
            URI(baseUrl).resolve(url).toString()
        } catch (_: Exception) {
            url
        }
    }
}
