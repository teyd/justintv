package expo.modules.twitchnet

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Routes only the Twitch playlist hosts through the user's proxy. Everything else, including
 * video segments, connects directly.
 */
object TwitchProxySelector : ProxySelector() {
  private val proxiedHosts = Regex(
    "^(usher\\.ttvnw\\.net|[a-z0-9-]+\\.playlist\\.(live-video|ttvnw)\\.net|video-weaver\\.[a-z0-9-]+\\.hls\\.ttvnw\\.net)$",
    RegexOption.IGNORE_CASE,
  )

  @Volatile
  private var proxy: Proxy? = null

  fun createProxy(host: String, port: Int, type: String): Proxy {
    val proxyType = if (type.equals("socks", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
    return Proxy(proxyType, InetSocketAddress.createUnresolved(host, port))
  }

  fun setProxy(host: String?, port: Int, type: String) {
    proxy = if (host.isNullOrBlank()) null else createProxy(host, port, type)
  }

  override fun select(uri: URI?): List<Proxy> {
    val host = uri?.host
    val current = proxy
    if (current != null && host != null && proxiedHosts.matches(host)) {
      return listOf(current)
    }
    return listOf(Proxy.NO_PROXY)
  }

  override fun connectFailed(uri: URI?, address: SocketAddress?, failure: IOException?) {}
}
