package dev.teyd.justintv.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.teyd.justintv.core.adfree.DefaultProxies
import dev.teyd.justintv.core.adfree.PlaylistResolver
import dev.teyd.justintv.core.adfree.PlaylistVerifier
import dev.teyd.justintv.core.adfree.ProxyHealthChecker
import dev.teyd.justintv.core.data.LanguageFilterStore
import dev.teyd.justintv.core.network.DirectorySource
import dev.teyd.justintv.core.network.GqlClient
import dev.teyd.justintv.core.network.OkHttpPlaylistFetcher
import dev.teyd.justintv.core.network.TwitchDirectoryApi
import dev.teyd.justintv.core.network.TwitchHttpClient
import dev.teyd.justintv.core.network.TwitchPlaybackApi
import dev.teyd.justintv.core.player.PlayerFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Probes (proxy playlists, ping) must fail fast: a hanging proxy costs seconds, not minutes. */
    private const val PROBE_CONNECT_TIMEOUT_SECONDS = 3L
    private const val VERIFY_CALL_TIMEOUT_SECONDS = 6L
    private const val PING_CALL_TIMEOUT_SECONDS = 4L

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = TwitchHttpClient.create()

    @Provides
    @Singleton
    fun twitchPlaybackApi(httpClient: OkHttpClient): TwitchPlaybackApi = TwitchPlaybackApi(httpClient)

    @Provides
    @Singleton
    fun playlistVerifier(httpClient: OkHttpClient): PlaylistVerifier =
        PlaylistVerifier(OkHttpPlaylistFetcher(probeClient(httpClient, VERIFY_CALL_TIMEOUT_SECONDS)))

    @Provides
    @Singleton
    fun playlistResolver(
        api: TwitchPlaybackApi,
        verifier: PlaylistVerifier,
    ): PlaylistResolver = PlaylistResolver(api = api, proxies = DefaultProxies.ALL, verifier = verifier)

    @Provides
    @Singleton
    fun proxyHealthChecker(httpClient: OkHttpClient): ProxyHealthChecker =
        ProxyHealthChecker(OkHttpPlaylistFetcher(probeClient(httpClient, PING_CALL_TIMEOUT_SECONDS)))

    @Provides
    @Singleton
    fun directorySource(httpClient: OkHttpClient): DirectorySource =
        TwitchDirectoryApi(GqlClient(httpClient))

    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

    @Provides
    @Singleton
    fun languageFilterStore(dataStore: DataStore<Preferences>): LanguageFilterStore =
        LanguageFilterStore(dataStore)

    @Provides
    @Singleton
    fun playerFactory(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
    ): PlayerFactory = PlayerFactory(context, httpClient)

    /** Shares the connection pool and dispatcher with [base] but gives up quickly. */
    private fun probeClient(base: OkHttpClient, callTimeoutSeconds: Long): OkHttpClient =
        base.newBuilder()
            .connectTimeout(PROBE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
}
