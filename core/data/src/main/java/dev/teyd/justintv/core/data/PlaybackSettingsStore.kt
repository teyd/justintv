package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether playback continues when the app is not on screen, and whether the screen is held
 * awake while the player is expanded.
 *
 * Background playback is off by default. The mini player is in-app only; that is the separate
 * "keep playing in the background" switch.
 */
class PlaybackSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val backgroundKey = booleanPreferencesKey("background_playback")
    private val pipKey = booleanPreferencesKey("picture_in_picture")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    val backgroundPlayback: Flow<Boolean> = dataStore.data.map { it[backgroundKey] == true }

    /** On by default: leaving the app shrinks the video into a system PiP window. */
    val pictureInPicture: Flow<Boolean> = dataStore.data.map { it[pipKey] != false }

    /** On by default: the expanded player holds the screen; the dock never does. */
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[keepScreenOnKey] != false }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        dataStore.edit { it[backgroundKey] = enabled }
    }

    suspend fun setPictureInPicture(enabled: Boolean) {
        dataStore.edit { it[pipKey] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[keepScreenOnKey] = enabled }
    }
}
