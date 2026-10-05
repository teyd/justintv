package dev.teyd.justintv.feature.streams

import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.model.ChannelPresence

/**
 * What the search field does with whatever has been typed.
 *
 * Autocomplete waits until [AUTO_SEARCH_LENGTH] characters, so a short prefix does not
 * hit Twitch on every key. The typed channel is still always a destination: opening it
 * does not wait for a result, and it stays the first row even when the list has not
 * arrived or does not contain that login.
 */
object ChannelSearch {
    const val AUTO_SEARCH_LENGTH = 4

    /** How long typing must pause before the autocomplete request is sent. */
    const val DEBOUNCE_MS = 300L

    /** What an offline channel shows in the title slot. */
    const val OFFLINE_LABEL = "Channel"

    /** Text sent to Twitch, or empty when the field has nothing to search. */
    fun searchText(raw: String): String = raw.trim().removePrefix("@").trim()

    /** Login the field currently names, or null when there is nothing to open. */
    fun typedLogin(raw: String): String? {
        val text = searchText(raw)
        if (text.isEmpty()) return null
        return text.lowercase()
    }

    fun shouldSearch(raw: String): Boolean = searchText(raw).length >= AUTO_SEARCH_LENGTH

    /**
     * The typed channel first, then the other hits.
     *
     * A hit with the same login replaces the bare row, so the icon, viewers and title
     * show up without listing that channel twice.
     */
    fun rows(
        raw: String,
        hits: List<ChannelHit>,
    ): List<ChannelHit> {
        val login = typedLogin(raw) ?: return emptyList()
        val typedName = searchText(raw).ifBlank { login }
        val match = hits.firstOrNull { it.login.equals(login, ignoreCase = true) }
        val direct =
            match ?: ChannelHit(
                id = login,
                login = login,
                displayName = typedName,
                avatarUrl = null,
                presence = ChannelPresence.Offline,
            )
        val rest = hits.filterNot { it.login.equals(login, ignoreCase = true) }
        return listOf(direct) + rest
    }

    /** Title of a live channel, or [OFFLINE_LABEL] when it is not on air. */
    fun subtitle(hit: ChannelHit): String =
        when (val presence = hit.presence) {
            is ChannelPresence.Live -> presence.title
            ChannelPresence.Offline -> OFFLINE_LABEL
        }

    /** Viewer count when the channel is live, otherwise nothing to show. */
    fun viewers(hit: ChannelHit): Int? =
        when (val presence = hit.presence) {
            is ChannelPresence.Live -> presence.viewerCount
            ChannelPresence.Offline -> null
        }
}
