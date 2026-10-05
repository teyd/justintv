package dev.teyd.justintv.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Creates the shared HTTP client. One client for the whole app: one connection pool, one TLS cache. */
object TwitchHttpClient {
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 15L

    fun create(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
}
