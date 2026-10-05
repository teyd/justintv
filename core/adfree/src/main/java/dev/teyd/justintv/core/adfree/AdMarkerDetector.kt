package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.adfree.AdMarkerRules.isAdDateRange
import dev.teyd.justintv.core.adfree.AdMarkerRules.isAdSegmentTitle

/**
 * Scans raw HLS playlist text for Twitch ad markers.
 *
 * This is used to verify a candidate stream *before* handing it to the player. The live
 * player does the same check against its parsed manifest via [AdMarkerRules].
 */
object AdMarkerDetector {

    fun hasAdMarkers(playlistText: String): Boolean {
        var lastSegmentWasAd = false

        for (rawLine in playlistText.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXT-X-DATERANGE:") -> {
                    if (isAdDateRangeLine(line)) return true
                    lastSegmentWasAd = false
                }

                line.startsWith("#EXTINF:") -> {
                    lastSegmentWasAd = isAdSegmentTitle(segmentTitle(line))
                }

                line.startsWith("#") -> lastSegmentWasAd = false

                // Anything else is a URI. An ad-titled EXTINF points at an ad segment.
                else -> if (lastSegmentWasAd) return true
            }
        }
        return false
    }

    private fun isAdDateRangeLine(line: String): Boolean {
        val attributes = parseAttributes(line.substringAfter(':'))
        return isAdDateRange(
            id = attributes["ID"],
            className = attributes["CLASS"],
            attributeNames = attributes.keys,
        )
    }

    /**
     * Parses the `KEY=VALUE` attribute list of an HLS tag.
     *
     * Values may be quoted and may contain commas (Twitch's trigger URLs do), so this walks
     * the string instead of splitting on commas.
     */
    fun parseAttributes(attributeList: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        var index = 0
        while (index < attributeList.length) {
            val equals = attributeList.indexOf('=', index)
            if (equals == -1) break

            val key = attributeList.substring(index, equals).trim().trimStart(',')
            index = equals + 1

            if (index < attributeList.length && attributeList[index] == '"') {
                val closing = attributeList.indexOf('"', index + 1)
                if (closing == -1) break
                attributes[key] = attributeList.substring(index + 1, closing)
                index = closing + 1
            } else {
                val comma = attributeList.indexOf(',', index)
                val end = if (comma == -1) attributeList.length else comma
                attributes[key] = attributeList.substring(index, end).trim()
                index = end
            }
        }
        return attributes
    }

    /** Extracts the title part of an `#EXTINF:<duration>,<title>` line. */
    fun segmentTitle(extinfLine: String): String =
        extinfLine.substringAfter(',', missingDelimiterValue = "").trim()
}
