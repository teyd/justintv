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
import dev.teyd.justintv.core.chat.BadgeRepository
import dev.teyd.justintv.core.chat.BttvBadgeProvider
import dev.teyd.justintv.core.chat.BttvProvider
import dev.teyd.justintv.core.chat.ChatHistorySettings
import dev.teyd.justintv.core.chat.ChatSession
import dev.teyd.justintv.core.chat.ChatterinoBadgeProvider
import dev.teyd.justintv.core.chat.EmoteRepository
import dev.teyd.justintv.core.chat.EmoteSource
import dev.teyd.justintv.core.chat.FfzBadgeProvider
import dev.teyd.justintv.core.chat.FfzProvider
import dev.teyd.justintv.core.chat.RecentMessages
import dev.teyd.justintv.core.chat.SevenTvBadges
import dev.teyd.justintv.core.chat.SevenTvProvider
import dev.teyd.justintv.core.chat.TwitchBadgeProvider
import dev.teyd.justintv.core.chat.TwitchEmoteProvider
import dev.teyd.justintv.core.chat.TwitchIrcClient
import dev.teyd.justintv.core.data.ChatSettingsStore
import dev.teyd.justintv.core.data.KeystoreTokenVault
import dev.teyd.justintv.core.model.ChatBadgeSource
import dev.teyd.justintv.core.network.ActiveNetworkDns
import dev.teyd.justintv.core.network.DirectorySource
import dev.teyd.justintv.core.network.GqlClient
import dev.teyd.justintv.core.network.JsonPoster
import dev.teyd.justintv.core.network.OkHttpJsonPoster
import dev.teyd.justintv.core.network.OkHttpTextFetcher
import dev.teyd.justintv.core.network.TokenVault
import dev.teyd.justintv.core.network.TwitchDirectoryApi
import dev.teyd.justintv.core.network.TwitchHttpClient
import dev.teyd.justintv.core.network.TwitchIdentityApi
import dev.teyd.justintv.core.network.TwitchPlaybackApi
import dev.teyd.justintv.core.network.TwitchSession
import dev.teyd.justintv.core.player.PlayerFactory
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Probes (proxy playlists, ping) must fail fast: a hanging proxy costs seconds, not minutes. */
    private const val PROBE_CONNECT_TIMEOUT_SECONDS = 3L
    private const val VERIFY_CALL_TIMEOUT_SECONDS = 6L
    private const val PING_CALL_TIMEOUT_SECONDS = 4L
    private const val PROVIDER_CALL_TIMEOUT_SECONDS = 10L
    private const val MAX_REQUESTS_PER_HOST = 16
    private const val MAX_REQUESTS = 64

    @Provides
    @Singleton
    fun okHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient =
        TwitchHttpClient.create(
            OkHttpClient
                .Builder()
                .dns(ActiveNetworkDns(context))
                // The default allows 5 at once per host. Emote thumbnails, chat emotes and the
                // prefetcher all come from the same few CDN hosts and would queue behind it.
                .dispatcher(
                    Dispatcher().apply {
                        maxRequestsPerHost = MAX_REQUESTS_PER_HOST
                        maxRequests = MAX_REQUESTS
                    },
                ),
        )

    @Provides
    @Singleton
    fun twitchPlaybackApi(httpClient: OkHttpClient): TwitchPlaybackApi = TwitchPlaybackApi(httpClient)

    @Provides
    @Singleton
    fun playlistVerifier(httpClient: OkHttpClient): PlaylistVerifier =
        PlaylistVerifier(OkHttpTextFetcher(probeClient(httpClient, VERIFY_CALL_TIMEOUT_SECONDS)))

    @Provides
    @Singleton
    fun playlistResolver(
        api: TwitchPlaybackApi,
        verifier: PlaylistVerifier,
    ): PlaylistResolver = PlaylistResolver(api = api, proxies = DefaultProxies.ALL, verifier = verifier)

    @Provides
    @Singleton
    fun proxyHealthChecker(httpClient: OkHttpClient): ProxyHealthChecker =
        ProxyHealthChecker(OkHttpTextFetcher(probeClient(httpClient, PING_CALL_TIMEOUT_SECONDS)))

    @Provides
    @Singleton
    fun directorySource(httpClient: OkHttpClient): DirectorySource = TwitchDirectoryApi(GqlClient(httpClient))

    @Provides
    @Singleton
    fun preferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

    @Provides
    @Singleton
    fun tokenVault(
        @ApplicationContext context: Context,
    ): TokenVault = KeystoreTokenVault(context)

    @Provides
    @Singleton
    fun twitchSession(
        httpClient: OkHttpClient,
        vault: TokenVault,
    ): TwitchSession =
        TwitchSession(
            api = TwitchIdentityApi(httpClient),
            vault = vault,
            clientId = dev.teyd.justintv.TwitchConfig.clientId,
        )

    @Provides
    @Singleton
    fun playerFactory(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
    ): PlayerFactory = PlayerFactory(context, httpClient)

    @Provides
    @Singleton
    fun twitchIrcClient(httpClient: OkHttpClient): TwitchIrcClient = TwitchIrcClient(httpClient)

    /** One repository for the whole app, so global emote sets are downloaded once per toggle set. */
    @Provides
    @Singleton
    fun emoteRepository(
        httpClient: OkHttpClient,
        chatSettings: ChatSettingsStore,
        session: TwitchSession,
    ): EmoteRepository {
        val probe = probeClient(httpClient, PROVIDER_CALL_TIMEOUT_SECONDS)
        val fetcher = OkHttpTextFetcher(probe)
        val helix = TwitchIdentityApi(probe)
        return EmoteRepository(
            providers =
                listOf(
                    TwitchEmoteProvider(
                        clientId = dev.teyd.justintv.TwitchConfig.clientId,
                        token = session::accessToken,
                        fetch = helix::authorizedGet,
                    ),
                    SevenTvProvider(fetcher),
                    BttvProvider(fetcher),
                    FfzProvider(fetcher),
                ),
            enabledSources =
                combine(chatSettings.sevenTv, chatSettings.bttv, chatSettings.ffz) { seven, bttv, ffz ->
                    buildSet {
                        add(EmoteSource.Twitch)
                        if (seven) add(EmoteSource.SevenTv)
                        if (bttv) add(EmoteSource.Bttv)
                        if (ffz) add(EmoteSource.Ffz)
                    }
                },
        )
    }

    /** Posts GraphQL bodies for badge lookups, with a short timeout so a hang is bounded. */
    @Provides
    @Singleton
    fun jsonPoster(httpClient: OkHttpClient): JsonPoster = OkHttpJsonPoster(probeClient(httpClient, PROVIDER_CALL_TIMEOUT_SECONDS))

    /**
     * One 7TV badge cache for the whole app. 7TV has no bulk users-to-badge list, so each
     * chatter is looked up once and remembered, negatives included.
     */
    @Provides
    @Singleton
    fun sevenTvBadges(poster: JsonPoster): SevenTvBadges = SevenTvBadges(poster)

    /** One repository for the whole app, so global badge sets are downloaded once per toggle set. */
    @Provides
    @Singleton
    fun badgeRepository(
        httpClient: OkHttpClient,
        chatSettings: ChatSettingsStore,
    ): BadgeRepository {
        val probe = probeClient(httpClient, PROVIDER_CALL_TIMEOUT_SECONDS)
        val fetcher = OkHttpTextFetcher(probe)
        val gql = GqlClient(probe)
        return BadgeRepository(
            providers =
                listOf(
                    TwitchBadgeProvider(gql::post),
                    ChatterinoBadgeProvider(fetcher),
                    FfzBadgeProvider(fetcher),
                    BttvBadgeProvider(fetcher),
                ),
            enabledSources =
                combine(
                    chatSettings.twitchBadges,
                    chatSettings.chatterinoBadges,
                    chatSettings.sevenTvBadges,
                    chatSettings.ffzBadges,
                    chatSettings.bttvBadges,
                ) { twitch, chatterino, sevenTv, ffz, bttv ->
                    buildSet {
                        if (twitch) add(ChatBadgeSource.Twitch)
                        if (chatterino) add(ChatBadgeSource.Chatterino)
                        if (sevenTv) add(ChatBadgeSource.SevenTv)
                        if (ffz) add(ChatBadgeSource.Ffz)
                        if (bttv) add(ChatBadgeSource.Bttv)
                    }
                },
        )
    }

    /** Not a singleton: each chat screen gets its own session and connection state. */
    @Provides
    fun chatSession(
        irc: TwitchIrcClient,
        emotes: EmoteRepository,
        badges: BadgeRepository,
        sevenTvBadges: SevenTvBadges,
        httpClient: OkHttpClient,
        chatSettings: ChatSettingsStore,
    ): ChatSession =
        ChatSession(
            irc = irc,
            emoteRepository = emotes,
            badgeRepository = badges,
            sevenTvBadges = sevenTvBadges,
            recent = RecentMessages(httpClient),
            historySettings = {
                ChatHistorySettings(
                    enabled = chatSettings.recentMessages.first(),
                    limit = chatSettings.recentMessageLimit.first(),
                )
            },
        )

    /** Shares the connection pool and dispatcher with [base] but gives up quickly. */
    private fun probeClient(
        base: OkHttpClient,
        callTimeoutSeconds: Long,
    ): OkHttpClient =
        base
            .newBuilder()
            .connectTimeout(PROBE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
}
