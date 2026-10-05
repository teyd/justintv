package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether playback continues when the app is not on screen.
 *
 * Off by default. The mini player is in-app only; this is the separate "keep playing in the
 * background" switch.
 */
class PlaybackSettingsStore(private val dataStore: DataStore<Preferences>) {

    private val key = booleanPreferencesKey("background_playback")

    val backgroundPlayback: Flow<Boolean> = dataStore.data.map { it[key] == true }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        dataStore.edit { it[key] = enabled }
    }
}
