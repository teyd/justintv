package dev.teyd.justintv.core.adfree

/**
 * The single source of truth for "is this an ad" decisions.
 *
 * Twitch marks server-side inserted ads in HLS playlists. The same rules are applied in two
 * places: the playlist text scanner ([AdMarkerDetector]) and the live player, which sees the
 * markers as Media3 interstitials.
 */
object AdMarkerRules {
    /** Segment titles used by Twitch's ad servers. */
    val AD_SEGMENT_TITLES = setOf("amazon", "adform", "dcm")

    /** Date-range id prefix for a stitched ad break. */
    const val STITCHED_AD_ID_PREFIX = "stitched-ad-"

    /** Date-range class for a stitched ad break. */
    const val STITCHED_AD_CLASS = "twitch-stitched-ad"

    /** Attribute prefix present on ad date ranges. */
    const val AD_ATTRIBUTE_PREFIX = "X-TV-TWITCH-AD-"

    fun isAdSegmentTitle(rawTitle: String?): Boolean {
        val title = rawTitle?.substringBefore('|')?.trim()?.lowercase() ?: return false
        return title in AD_SEGMENT_TITLES
    }

    fun isAdDateRange(
        id: String?,
        className: String?,
        attributeNames: Collection<String>,
    ): Boolean {
        if (id?.startsWith(STITCHED_AD_ID_PREFIX) == true) return true
        if (className == STITCHED_AD_CLASS) return true
        return attributeNames.any { it.startsWith(AD_ATTRIBUTE_PREFIX) }
    }
}
