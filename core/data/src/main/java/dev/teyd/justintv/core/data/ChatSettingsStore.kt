package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How large chat lines draw. Emotes scale with the text so lines keep their rhythm. */
enum class ChatTextSize(
    val textSp: Int,
    val emoteSp: Int,
) {
    Small(12, 20),
    Default(14, 24),
    Large(17, 29),
}

/** How a chat line's timestamp is written. System follows the device's 12/24-hour setting. */
enum class ChatTimeFormat {
    System,
    Hour12,
    Hour24,
}

/**
 * Chat preferences: recent-message history, which third-party emote providers are used, and
 * how names and text draw, and whether each line shows the time it was sent.
 *
 * Switches default to on, except timestamps; the limit only applies while [recentMessages] is on.
 */
class ChatSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val recentMessagesKey = booleanPreferencesKey("chat_recent_messages")
    private val recentMessageLimitKey = intPreferencesKey("chat_recent_message_limit")
    private val sevenTvKey = booleanPreferencesKey("chat_7tv")
    private val bttvKey = booleanPreferencesKey("chat_bttv")
    private val ffzKey = booleanPreferencesKey("chat_ffz")
    private val showInputKey = booleanPreferencesKey("chat_show_input")
    private val coloredUsernamesKey = booleanPreferencesKey("chat_colored_usernames")
    private val chatTextSizeKey = stringPreferencesKey("chat_text_size")
    private val showTimestampsKey = booleanPreferencesKey("chat_show_timestamps")
    private val timeFormatKey = stringPreferencesKey("chat_time_format")

    /** On by default: chat starts with the last messages instead of empty. */
    val recentMessages: Flow<Boolean> = dataStore.data.map { it[recentMessagesKey] != false }

    /** How many history messages to request. */
    val recentMessageLimit: Flow<Int> =
        dataStore.data.map {
            (it[recentMessageLimitKey] ?: DEFAULT_RECENT_MESSAGE_LIMIT)
                .coerceIn(MIN_RECENT_MESSAGE_LIMIT, MAX_RECENT_MESSAGE_LIMIT)
        }

    val sevenTv: Flow<Boolean> = dataStore.data.map { it[sevenTvKey] != false }

    val bttv: Flow<Boolean> = dataStore.data.map { it[bttvKey] != false }

    val ffz: Flow<Boolean> = dataStore.data.map { it[ffzKey] != false }

    /** On by default. The box is still hidden until the viewer is signed in. */
    val showInput: Flow<Boolean> = dataStore.data.map { it[showInputKey] != false }

    /** On by default: names keep the colour Twitch sends, or the one hashed from the nick. */
    val coloredUsernames: Flow<Boolean> = dataStore.data.map { it[coloredUsernamesKey] != false }

    /** [ChatTextSize.Default] unless the viewer picked another. */
    val chatTextSize: Flow<ChatTextSize> = dataStore.data.map { chatTextSizeOf(it[chatTextSizeKey]) }

    /** Off by default: chat stays as plain text until the viewer asks for times. */
    val showTimestamps: Flow<Boolean> = dataStore.data.map { it[showTimestampsKey] == true }

    /** System by default, so a fresh install follows the device clock. */
    val timeFormat: Flow<ChatTimeFormat> =
        dataStore.data.map { chatTimeFormatOf(it[timeFormatKey]) }

    suspend fun setRecentMessages(enabled: Boolean) {
        dataStore.edit { it[recentMessagesKey] = enabled }
    }

    suspend fun setRecentMessageLimit(limit: Int) {
        dataStore.edit {
            it[recentMessageLimitKey] = limit.coerceIn(MIN_RECENT_MESSAGE_LIMIT, MAX_RECENT_MESSAGE_LIMIT)
        }
    }

    suspend fun setSevenTv(enabled: Boolean) {
        dataStore.edit { it[sevenTvKey] = enabled }
    }

    suspend fun setBttv(enabled: Boolean) {
        dataStore.edit { it[bttvKey] = enabled }
    }

    suspend fun setFfz(enabled: Boolean) {
        dataStore.edit { it[ffzKey] = enabled }
    }

    suspend fun setShowInput(enabled: Boolean) {
        dataStore.edit { it[showInputKey] = enabled }
    }

    suspend fun setColoredUsernames(enabled: Boolean) {
        dataStore.edit { it[coloredUsernamesKey] = enabled }
    }

    suspend fun setChatTextSize(size: ChatTextSize) {
        dataStore.edit { it[chatTextSizeKey] = size.name }
    }

    suspend fun setShowTimestamps(enabled: Boolean) {
        dataStore.edit { it[showTimestampsKey] = enabled }
    }

    suspend fun setTimeFormat(format: ChatTimeFormat) {
        dataStore.edit { it[timeFormatKey] = format.name }
    }

    companion object {
        const val DEFAULT_RECENT_MESSAGE_LIMIT = 80
        const val MIN_RECENT_MESSAGE_LIMIT = 1
        const val MAX_RECENT_MESSAGE_LIMIT = 800

        /** Unknown or missing values fall back to [ChatTextSize.Default]. */
        fun chatTextSizeOf(stored: String?): ChatTextSize =
            when (stored) {
                ChatTextSize.Small.name -> ChatTextSize.Small
                ChatTextSize.Large.name -> ChatTextSize.Large
                else -> ChatTextSize.Default
            }

        fun chatTimeFormatOf(stored: String?): ChatTimeFormat =
            when (stored) {
                ChatTimeFormat.Hour12.name -> ChatTimeFormat.Hour12
                ChatTimeFormat.Hour24.name -> ChatTimeFormat.Hour24
                else -> ChatTimeFormat.System
            }
    }
}
