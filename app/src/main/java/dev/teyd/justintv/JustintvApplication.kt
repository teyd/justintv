package dev.teyd.justintv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import dev.teyd.justintv.core.data.PlaybackSettingsStore
import dev.teyd.justintv.core.player.PlaybackGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class JustintvApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var httpClient: OkHttpClient

    @Inject
    lateinit var playbackGate: PlaybackGate

    @Inject
    lateinit var playbackSettings: PlaybackSettingsStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startedActivities = 0

    /**
     * One image loader for the app. Animated decoding is on because most 7TV, BTTV and FFZ
     * emotes are animated WebP or GIF, and the shared OkHttp client keeps their traffic on one
     * connection pool.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                add(AnimatedImageDecoder.Factory())
            }.build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            playbackSettings.backgroundPlayback.collect { playbackGate.allowBackground = it }
        }
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    if (startedActivities == 1) playbackGate.onForeground()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                    val inPip = activity.isInPictureInPictureMode
                    if (startedActivities == 0 && !inPip) playbackGate.onBackground()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
