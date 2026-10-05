package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Chat preferences: recent-message history and which third-party emote providers are used.
 *
 * All switches default to on; the limit only applies while [recentMessages] is on.
 */
class ChatSettingsStore(private val dataStore: DataStore<Preferences>) {

    private val recentMessagesKey = booleanPreferencesKey("chat_recent_messages")
    private val recentMessageLimitKey = intPreferencesKey("chat_recent_message_limit")
    private val sevenTvKey = booleanPreferencesKey("chat_7tv")
    private val bttvKey = booleanPreferencesKey("chat_bttv")
    private val ffzKey = booleanPreferencesKey("chat_ffz")
    private val showInputKey = booleanPreferencesKey("chat_show_input")

    /** On by default: chat starts with the last messages instead of empty. */
    val recentMessages: Flow<Boolean> = dataStore.data.map { it[recentMessagesKey] != false }

    /** How many history messages to request. */
    val recentMessageLimit: Flow<Int> = dataStore.data.map {
        (it[recentMessageLimitKey] ?: DEFAULT_RECENT_MESSAGE_LIMIT)
            .coerceIn(MIN_RECENT_MESSAGE_LIMIT, MAX_RECENT_MESSAGE_LIMIT)
    }

    val sevenTv: Flow<Boolean> = dataStore.data.map { it[sevenTvKey] != false }

    val bttv: Flow<Boolean> = dataStore.data.map { it[bttvKey] != false }

    val ffz: Flow<Boolean> = dataStore.data.map { it[ffzKey] != false }

    /** On by default. The box is still hidden until the viewer is signed in. */
    val showInput: Flow<Boolean> = dataStore.data.map { it[showInputKey] != false }

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

    companion object {
        const val DEFAULT_RECENT_MESSAGE_LIMIT = 80
        const val MIN_RECENT_MESSAGE_LIMIT = 1
        const val MAX_RECENT_MESSAGE_LIMIT = 800
    }
}
