package dev.teyd.justintv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class JustintvApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var httpClient: OkHttpClient

    /**
     * One image loader for the app. Animated decoding is on because most 7TV, BTTV and FFZ
     * emotes are animated WebP or GIF, and the shared OkHttp client keeps their traffic on one
     * connection pool.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
