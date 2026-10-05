package dev.teyd.justintv.core.adfree

/**
 * An m3u8 proxy: a server that fetches a channel's playlist from an ad-free region and
 * returns it, so the playlist the player receives has no server-side ads stitched in.
 *
 * Video segments are never proxied; the URLs inside the returned playlist point at Twitch's
 * own CDN.
 */
data class ProxyEndpoint(
    val host: String,
    val note: String? = null,
) {
    fun liveUrl(login: String): String = "https://$host/live/${login.lowercase()}?allow_source=true&allow_audio_only=true&fast_bread=true"

    fun pingUrl(): String = "https://$host/ping"

    override fun toString(): String = host
}

/**
 * Shipped defaults.
 *
 * Probing is parallel and the fastest clean answer wins, so the order here is only a
 * tie-break and the list can safely contain hosts that are sometimes down.
 *
 * - PerfProd's public m3u8 proxies.
 * - Luminous public instances (open-source `luminous-ttv`).
 *
 * Users can replace the list in settings (M6).
 */
object DefaultProxies {
    val ALL: List<ProxyEndpoint> =
        listOf(
            ProxyEndpoint("eu.luminous.dev", "Luminous · Europe"),
            ProxyEndpoint("eu2.luminous.dev", "Luminous · Europe 2"),
            ProxyEndpoint("lb-eu5.cdn-perfprod.com", "PerfProd · Europe 5"),
            ProxyEndpoint("lb-eu.cdn-perfprod.com", "PerfProd · Europe"),
            ProxyEndpoint("lb-as.cdn-perfprod.com", "PerfProd · Asia"),
            ProxyEndpoint("as.luminous.dev", "Luminous · Asia"),
            ProxyEndpoint("lb-eu2.cdn-perfprod.com", "PerfProd · Europe 2"),
            ProxyEndpoint("lb-eu4.cdn-perfprod.com", "PerfProd · Europe 4"),
            ProxyEndpoint("lb-eu3.cdn-perfprod.com", "PerfProd · Europe 3"),
            ProxyEndpoint("lb-na.cdn-perfprod.com", "PerfProd · North America"),
            ProxyEndpoint("lb-sa.cdn-perfprod.com", "PerfProd · South America"),
        )
}
