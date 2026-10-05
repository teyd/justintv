package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the viewer is logged in, and their token.
 *
 * Nothing writes a token yet: login lands in its own change, and until then the app behaves as
 * logged out everywhere. The screens already read this, so wiring login in is a matter of
 * calling [save].
 */
class SessionStore(private val dataStore: DataStore<Preferences>) {

    private val tokenKey = stringPreferencesKey("access_token")

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { !it[tokenKey].isNullOrBlank() }

    val accessToken: Flow<String?> = dataStore.data.map { it[tokenKey]?.takeIf(String::isNotBlank) }

    suspend fun save(accessToken: String) {
        dataStore.edit { it[tokenKey] = accessToken }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(tokenKey) }
    }
}
