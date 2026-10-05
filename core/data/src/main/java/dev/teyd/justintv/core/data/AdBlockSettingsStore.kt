package dev.teyd.justintv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Ad-blocking behaviour: the master switch and which m3u8 proxies may be used.
 *
 * Disabled hosts are stored instead of enabled ones, so proxies added in a later build are on
 * by default and the preference only records the exceptions.
 */
class AdBlockSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val enabledKey = booleanPreferencesKey("ad_block_enabled")
    private val disabledProxiesKey = stringSetPreferencesKey("ad_block_disabled_proxies")

    /** On by default: streams are checked through the proxies for ad markers. */
    val adBlockEnabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] != false }

    /** Proxy hosts the viewer switched off. */
    val disabledProxies: Flow<Set<String>> = dataStore.data.map { it[disabledProxiesKey].orEmpty() }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setProxyEnabled(
        host: String,
        enabled: Boolean,
    ) {
        dataStore.edit { preferences ->
            val disabled = preferences[disabledProxiesKey].orEmpty()
            preferences[disabledProxiesKey] = if (enabled) disabled - host else disabled + host
        }
    }
}
