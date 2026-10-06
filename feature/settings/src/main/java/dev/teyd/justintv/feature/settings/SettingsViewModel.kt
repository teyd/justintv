package dev.teyd.justintv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.adfree.DefaultProxies
import dev.teyd.justintv.core.adfree.ProxyEndpoint
import dev.teyd.justintv.core.adfree.ProxyHealthChecker
import dev.teyd.justintv.core.data.AdBlockSettingsStore
import dev.teyd.justintv.core.data.AppearanceSettingsStore
import dev.teyd.justintv.core.data.ChatSettingsStore
import dev.teyd.justintv.core.data.ChatTextSize
import dev.teyd.justintv.core.data.ChatTimeFormat
import dev.teyd.justintv.core.data.PlaybackSettingsStore
import dev.teyd.justintv.core.data.ThemeMode
import dev.teyd.justintv.core.model.ChatBadgeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProxyStatus(
    val proxy: ProxyEndpoint,
    /** null while the check is running. */
    val online: Boolean? = null,
    /** Whether the proxy may be used for playback. */
    val enabled: Boolean = true,
)

data class SettingsUiState(
    val backgroundPlayback: Boolean = false,
    val pictureInPicture: Boolean = true,
    val keepScreenOn: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val proxyStatuses: List<ProxyStatus> = DefaultProxies.ALL.map { ProxyStatus(it) },
    val isCheckingProxies: Boolean = false,
    val recentMessages: Boolean = true,
    val recentMessageLimit: Int = ChatSettingsStore.DEFAULT_RECENT_MESSAGE_LIMIT,
    val sevenTv: Boolean = true,
    val bttv: Boolean = true,
    val ffz: Boolean = true,
    val badges: Set<ChatBadgeSource> = ChatBadgeSource.entries.toSet(),
    val showChatInput: Boolean = true,
    val coloredUsernames: Boolean = true,
    val chatTextSize: ChatTextSize = ChatTextSize.Default,
    val showTimestamps: Boolean = false,
    val timeFormat: ChatTimeFormat = ChatTimeFormat.System,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val healthChecker: ProxyHealthChecker,
        private val playbackSettings: PlaybackSettingsStore,
        private val adBlockSettings: AdBlockSettingsStore,
        private val chatSettings: ChatSettingsStore,
        private val appearanceSettings: AppearanceSettingsStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                combine(
                    combine(
                        playbackSettings.backgroundPlayback,
                        playbackSettings.pictureInPicture,
                        adBlockSettings.adBlockEnabled,
                        adBlockSettings.disabledProxies,
                        appearanceSettings.themeMode,
                    ) { background, pip, adBlock, disabled, theme ->
                        PlaybackSlice(background, pip, adBlock, disabled, theme)
                    },
                    combine(
                        chatSettings.recentMessages,
                        chatSettings.recentMessageLimit,
                        chatSettings.sevenTv,
                        chatSettings.bttv,
                        chatSettings.ffz,
                    ) { recent, limit, seven, bttv, ffz ->
                        ChatSlice(recent, limit, seven, bttv, ffz)
                    },
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
                    appearanceSettings.dynamicColor,
                ) { playback, chat, badges, dynamic ->
                    SettingsSlice(playback, chat, badges, dynamic)
                }.collect { slice ->
                    _state.update { current ->
                        current.copy(
                            backgroundPlayback = slice.playback.background,
                            pictureInPicture = slice.playback.pip,
                            adBlockEnabled = slice.playback.adBlock,
                            themeMode = slice.playback.theme,
                            proxyStatuses =
                                current.proxyStatuses.map {
                                    it.copy(enabled = it.proxy.host !in slice.playback.disabled)
                                },
                            recentMessages = slice.chat.recent,
                            recentMessageLimit = slice.chat.limit,
                            sevenTv = slice.chat.seven,
                            bttv = slice.chat.bttv,
                            ffz = slice.chat.ffz,
                            badges = slice.badges,
                            dynamicColor = slice.dynamic,
                        )
                    }
                }
            }
            viewModelScope.launch {
                combine(
                    chatSettings.showInput,
                    chatSettings.coloredUsernames,
                    chatSettings.chatTextSize,
                ) { showInput, colored, textSize -> ChatDisplaySlice(showInput, colored, textSize) }
                    .collect { display ->
                        _state.update {
                            it.copy(
                                showChatInput = display.showInput,
                                coloredUsernames = display.coloredUsernames,
                                chatTextSize = display.chatTextSize,
                            )
                        }
                    }
            }
            viewModelScope.launch {
                playbackSettings.keepScreenOn.collect { enabled ->
                    _state.update { it.copy(keepScreenOn = enabled) }
                }
            }
            viewModelScope.launch {
                chatSettings.showTimestamps.collect { enabled ->
                    _state.update { it.copy(showTimestamps = enabled) }
                }
            }
            viewModelScope.launch {
                chatSettings.timeFormat.collect { format ->
                    _state.update { it.copy(timeFormat = format) }
                }
            }
            checkProxies()
        }

        fun setBackgroundPlayback(enabled: Boolean) {
            viewModelScope.launch { playbackSettings.setBackgroundPlayback(enabled) }
        }

        fun setPictureInPicture(enabled: Boolean) {
            viewModelScope.launch { playbackSettings.setPictureInPicture(enabled) }
        }

        fun setKeepScreenOn(enabled: Boolean) {
            viewModelScope.launch { playbackSettings.setKeepScreenOn(enabled) }
        }

        fun setAdBlockEnabled(enabled: Boolean) {
            viewModelScope.launch { adBlockSettings.setAdBlockEnabled(enabled) }
        }

        fun setProxyEnabled(
            proxy: ProxyEndpoint,
            enabled: Boolean,
        ) {
            viewModelScope.launch { adBlockSettings.setProxyEnabled(proxy.host, enabled) }
        }

        fun setRecentMessages(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setRecentMessages(enabled) }
        }

        fun setRecentMessageLimit(limit: Int) {
            viewModelScope.launch { chatSettings.setRecentMessageLimit(limit) }
        }

        fun setSevenTv(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setSevenTv(enabled) }
        }

        fun setBttv(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setBttv(enabled) }
        }

        fun setFfz(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setFfz(enabled) }
        }

        fun setShowChatInput(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setShowInput(enabled) }
        }

        fun setColoredUsernames(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setColoredUsernames(enabled) }
        }

        fun setChatTextSize(size: ChatTextSize) {
            viewModelScope.launch { chatSettings.setChatTextSize(size) }
        }

        fun setShowTimestamps(enabled: Boolean) {
            viewModelScope.launch { chatSettings.setShowTimestamps(enabled) }
        }

        fun setTimeFormat(format: ChatTimeFormat) {
            viewModelScope.launch { chatSettings.setTimeFormat(format) }
        }

        fun setBadgeSource(
            source: ChatBadgeSource,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                when (source) {
                    ChatBadgeSource.Twitch -> chatSettings.setTwitchBadges(enabled)
                    ChatBadgeSource.Chatterino -> chatSettings.setChatterinoBadges(enabled)
                    ChatBadgeSource.SevenTv -> chatSettings.setSevenTvBadges(enabled)
                    ChatBadgeSource.Ffz -> chatSettings.setFfzBadges(enabled)
                    ChatBadgeSource.Bttv -> chatSettings.setBttvBadges(enabled)
                }
            }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { appearanceSettings.setThemeMode(mode) }
        }

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch { appearanceSettings.setDynamicColor(enabled) }
        }

        /**
         * Checks every proxy at once. Each result appears as soon as it arrives, so one hanging
         * host cannot hold up the others. The health checker's own timeout bounds the slowest.
         */
        fun checkProxies() {
            if (_state.value.isCheckingProxies) return
            viewModelScope.launch {
                _state.update { current ->
                    current.copy(
                        isCheckingProxies = true,
                        proxyStatuses = current.proxyStatuses.map { it.copy(online = null) },
                    )
                }
                coroutineScope {
                    _state.value.proxyStatuses.forEach { status ->
                        launch {
                            val online = healthChecker.isOnline(status.proxy)
                            _state.update { current ->
                                current.copy(
                                    proxyStatuses =
                                        current.proxyStatuses.map {
                                            if (it.proxy == status.proxy) it.copy(online = online) else it
                                        },
                                )
                            }
                        }
                    }
                }
                _state.update { it.copy(isCheckingProxies = false) }
            }
        }

        companion object {
            /** Offered history sizes; small enough to read, big enough to fill a chat pane. */
            val MESSAGE_LIMIT_OPTIONS = listOf(20, 50, 80, 150)
        }
    }

private data class PlaybackSlice(
    val background: Boolean,
    val pip: Boolean,
    val adBlock: Boolean,
    val disabled: Set<String>,
    val theme: ThemeMode,
)

private data class ChatSlice(
    val recent: Boolean,
    val limit: Int,
    val seven: Boolean,
    val bttv: Boolean,
    val ffz: Boolean,
)

private data class SettingsSlice(
    val playback: PlaybackSlice,
    val chat: ChatSlice,
    val badges: Set<ChatBadgeSource>,
    val dynamic: Boolean,
)

private data class ChatDisplaySlice(
    val showInput: Boolean,
    val coloredUsernames: Boolean,
    val chatTextSize: ChatTextSize,
)
