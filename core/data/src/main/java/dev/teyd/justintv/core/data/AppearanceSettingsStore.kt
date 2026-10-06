package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Which Material palette the app draws with. */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * Appearance preferences.
 *
 * System is the default, so a fresh install follows the device. Dynamic color is off: the
 * purple accent is the identity unless the viewer asks for the wallpaper palette.
 */
@Singleton
class AppearanceSettingsStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private val modeKey = stringPreferencesKey("theme_mode")
        private val dynamicKey = booleanPreferencesKey("dynamic_color")

        val themeMode: Flow<ThemeMode> =
            dataStore.data.map { preferences ->
                themeModeOf(preferences[modeKey])
            }

        val dynamicColor: Flow<Boolean> = dataStore.data.map { it[dynamicKey] == true }

        suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { it[modeKey] = mode.name }
        }

        suspend fun setDynamicColor(enabled: Boolean) {
            dataStore.edit { it[dynamicKey] = enabled }
        }

        companion object {
            fun themeModeOf(stored: String?): ThemeMode =
                when (stored) {
                    ThemeMode.Light.name -> ThemeMode.Light
                    ThemeMode.Dark.name -> ThemeMode.Dark
                    else -> ThemeMode.System
                }
        }
    }
