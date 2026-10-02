package com.mtv.iptv.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mtv_prefs")

class UserPrefs(private val context: Context) {
    private val lastServerIdKey = longPreferencesKey("last_server_id")
    private val usernameKey = stringPreferencesKey("xtream_username")
    private val passwordKey = stringPreferencesKey("xtream_password")

    val lastServerId: Flow<Long?> = context.dataStore.data.map { it[lastServerIdKey] }

    suspend fun setLastServerId(id: Long) {
        context.dataStore.edit { it[lastServerIdKey] = id }
    }

    /** Credenciales Xtream recordadas (para no pedirlas en cada arranque). */
    suspend fun getUsername(): String =
        context.dataStore.data.map { it[usernameKey].orEmpty() }.first()

    suspend fun getPassword(): String =
        context.dataStore.data.map { it[passwordKey].orEmpty() }.first()

    suspend fun setCredentials(username: String, password: String) {
        context.dataStore.edit {
            it[usernameKey] = username
            it[passwordKey] = password
        }
    }
}
