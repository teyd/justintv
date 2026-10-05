package dev.teyd.justintv.core.adfree

import dev.teyd.justintv.core.network.TextFetcher

/**
 * Fetches a candidate playlist and decides whether it carries ads.
 *
 * Master playlists are followed one level down to the first variant's media playlist,
 * because that is where Twitch stitches ad markers.
 */
class PlaylistVerifier(
    private val fetcher: TextFetcher,
) {
    suspend fun isAdFree(playlistUrl: String): Boolean {
        val playlist = fetcher.fetchText(playlistUrl)
        if (PlaylistParser.isMediaPlaylist(playlist)) {
            return !AdMarkerDetector.hasAdMarkers(playlist)
        }
        val mediaUrl =
            PlaylistParser.findMediaPlaylistUrl(playlist, baseUrl = playlistUrl)
                ?: return true // Nothing to inspect: accept rather than reject a workable stream.
        val media = fetcher.fetchText(mediaUrl)
        return !AdMarkerDetector.hasAdMarkers(media)
    }
}
