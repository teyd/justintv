package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.teyd.justintv.core.model.StreamLanguages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The languages the viewer wants to see in the directory.
 *
 * An empty set means "all languages", which is the default.
 */
@Singleton
class LanguageFilterStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private val key = stringSetPreferencesKey("stream_languages")

        val languages: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                StreamLanguages.sanitize(preferences[key].orEmpty()).toSet()
            }

        suspend fun set(languages: Set<String>) {
            dataStore.edit { preferences ->
                preferences[key] = StreamLanguages.sanitize(languages).toSet()
            }
        }
    }
