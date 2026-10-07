package expo.modules.twitchnet

import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.HttpURLConnection
import java.net.ProxySelector
import java.net.URL

private const val TEST_URL = "https://usher.ttvnw.net/"
private const val TEST_TIMEOUT_MS = 6000

class TwitchNetModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("TwitchNet")

    OnCreate {
      ProxySelector.setDefault(TwitchProxySelector)
    }

    Function("setProxy") { host: String?, port: Int, type: String ->
      TwitchProxySelector.setProxy(host, port, type)
    }

    AsyncFunction("testProxy") { host: String, port: Int, type: String ->
      val proxy = TwitchProxySelector.createProxy(host, port, type)
      val started = System.nanoTime()
      try {
        val connection = URL(TEST_URL).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = TEST_TIMEOUT_MS
        connection.readTimeout = TEST_TIMEOUT_MS
        val status = connection.responseCode
        connection.disconnect()
        mapOf("status" to status, "ms" to (System.nanoTime() - started) / 1_000_000)
      } catch (e: Exception) {
        throw CodedException("ERR_PROXY_TEST", e.message ?: e.javaClass.simpleName, e)
      }
    }
  }
}
