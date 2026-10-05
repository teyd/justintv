package dev.teyd.justintv

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.teyd.justintv.core.adfree.DefaultProxies
import dev.teyd.justintv.core.adfree.PlaylistResolver
import dev.teyd.justintv.core.adfree.PlaylistVerifier
import dev.teyd.justintv.core.adfree.ProxyHealthChecker
import dev.teyd.justintv.core.network.TwitchHttpClient
import dev.teyd.justintv.core.network.TwitchPlaybackApi
import dev.teyd.justintv.core.player.PlayerFactory
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = TwitchHttpClient.create()

    @Provides
    @Singleton
    fun twitchPlaybackApi(httpClient: OkHttpClient): TwitchPlaybackApi = TwitchPlaybackApi(httpClient)

    @Provides
    @Singleton
    fun playlistVerifier(api: TwitchPlaybackApi): PlaylistVerifier = PlaylistVerifier(api)

    @Provides
    @Singleton
    fun playlistResolver(
        api: TwitchPlaybackApi,
        verifier: PlaylistVerifier,
    ): PlaylistResolver = PlaylistResolver(api = api, proxies = DefaultProxies.ALL, verifier = verifier)

    @Provides
    @Singleton
    fun proxyHealthChecker(api: TwitchPlaybackApi): ProxyHealthChecker = ProxyHealthChecker(api)

    @Provides
    @Singleton
    fun playerFactory(
        @ApplicationContext context: android.content.Context,
        httpClient: OkHttpClient,
    ): PlayerFactory = PlayerFactory(context, httpClient)
}
