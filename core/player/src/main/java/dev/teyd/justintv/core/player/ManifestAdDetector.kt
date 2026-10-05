package dev.teyd.justintv.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import dev.teyd.justintv.core.adfree.AdMarkerRules

/**
 * Detects ad markers in the manifest the player is currently playing.
 *
 * This is the live counterpart of the pre-flight playlist check: Twitch can start a mid-roll
 * at any time, which only shows up here. Same rules object, so both checks stay in sync.
 */
@OptIn(UnstableApi::class)
object ManifestAdDetector {
    fun hasAdMarkers(manifest: HlsManifest?): Boolean {
        val playlist = manifest?.mediaPlaylist ?: return false

        val lastSegmentTitle = playlist.segments.lastOrNull()?.title
        if (AdMarkerRules.isAdSegmentTitle(lastSegmentTitle)) return true

        return playlist.interstitials.any(::isAd)
    }

    /** True when the ad marker covers the live edge, meaning the viewer is in the ad now. */
    fun isAdAtLiveEdge(manifest: HlsManifest?): Boolean {
        val playlist = manifest?.mediaPlaylist ?: return false
        val segment = playlist.segments.lastOrNull() ?: return false
        val segmentStartUs = playlist.startTimeUs + segment.relativeStartTimeUs

        return playlist.interstitials.any { interstitial ->
            if (!isAd(interstitial)) return@any false

            val end = interstitialEndUs(interstitial) ?: return@any false
            segmentStartUs in interstitial.startDateUnixUs until end
        }
    }

    private fun isAd(interstitial: HlsMediaPlaylist.Interstitial): Boolean =
        AdMarkerRules.isAdDateRange(
            id = interstitial.id,
            className =
                interstitial.clientDefinedAttributes
                    .firstOrNull { it.name == "CLASS" }
                    ?.textValue,
            attributeNames = interstitial.clientDefinedAttributes.map { it.name },
        )

    private fun interstitialEndUs(interstitial: HlsMediaPlaylist.Interstitial): Long? =
        when {
            interstitial.endDateUnixUs != C.TIME_UNSET -> {
                interstitial.endDateUnixUs
            }

            interstitial.durationUs != C.TIME_UNSET -> {
                interstitial.startDateUnixUs + interstitial.durationUs
            }

            interstitial.plannedDurationUs != C.TIME_UNSET -> {
                interstitial.startDateUnixUs + interstitial.plannedDurationUs
            }

            else -> {
                null
            }
        }
}
