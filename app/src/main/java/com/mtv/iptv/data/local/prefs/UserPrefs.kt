package com.mtv.iptv.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

    /**
     * Borra las credenciales guardadas en plano (migración a SecurePrefs:
     * se leen una vez, se guardan cifradas y se borran de aquí).
     */
    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(usernameKey)
            it.remove(passwordKey)
        }
    }

    private val sortVodKey = stringPreferencesKey("sort_vod")
    private val sortSeriesKey = stringPreferencesKey("sort_series")

    /** Orden del catálogo ("nombre" | "recientes" | "rating" | "anio"). Default "nombre". */
    val sortVod: Flow<String> = context.dataStore.data.map { it[sortVodKey] ?: "nombre" }
    val sortSeries: Flow<String> = context.dataStore.data.map { it[sortSeriesKey] ?: "nombre" }

    suspend fun setSortVod(value: String) {
        context.dataStore.edit { it[sortVodKey] = value }
    }

    suspend fun setSortSeries(value: String) {
        context.dataStore.edit { it[sortSeriesKey] = value }
    }

    // ---------------- Reproducción ----------------

    private val defaultSpeedKey = floatPreferencesKey("default_speed")

    /** Velocidad de reproducción por defecto. Default 1f. */
    val defaultSpeed: Flow<Float> = context.dataStore.data.map { it[defaultSpeedKey] ?: 1f }

    suspend fun setDefaultSpeed(value: Float) {
        context.dataStore.edit { it[defaultSpeedKey] = value }
    }

    private val autoplayNextKey = booleanPreferencesKey("autoplay_next")

    /** Reproducir automáticamente el siguiente episodio al terminar uno. Default false. */
    val autoplayNext: Flow<Boolean> = context.dataStore.data.map { it[autoplayNextKey] ?: false }

    suspend fun setAutoplayNext(value: Boolean) {
        context.dataStore.edit { it[autoplayNextKey] = value }
    }

    private val pipEnabledKey = booleanPreferencesKey("pip_enabled")

    /** Mostrar el botón de Picture-in-Picture en el reproductor. Default true. */
    val pipEnabled: Flow<Boolean> = context.dataStore.data.map { it[pipEnabledKey] ?: true }

    suspend fun setPipEnabled(value: Boolean) {
        context.dataStore.edit { it[pipEnabledKey] = value }
    }

    private val resumeEnabledKey = booleanPreferencesKey("resume_enabled")

    /** Mostrar el diálogo "Continuar viendo" (sino arranca desde 0). Default true. */
    val resumeEnabled: Flow<Boolean> = context.dataStore.data.map { it[resumeEnabledKey] ?: true }

    suspend fun setResumeEnabled(value: Boolean) {
        context.dataStore.edit { it[resumeEnabledKey] = value }
    }

    private val playerModeKey = stringPreferencesKey("player_mode")

    /** Reproductor ("auto" | "internal" | "external"). Default "auto". */
    val playerMode: Flow<String> = context.dataStore.data.map { it[playerModeKey] ?: "auto" }

    suspend fun setPlayerMode(value: String) {
        context.dataStore.edit { it[playerModeKey] = value }
    }

    // ---------------- Descargas ----------------

    private val dlQualityKey = stringPreferencesKey("dl_quality")

    /** Calidad de descarga ("auto" | "alta" | "media" | "baja"). Default "alta". */
    val dlQuality: Flow<String> = context.dataStore.data.map { it[dlQualityKey] ?: "alta" }

    suspend fun setDlQuality(value: String) {
        context.dataStore.edit { it[dlQualityKey] = value }
    }

    private val dlWifiOnlyKey = booleanPreferencesKey("dl_wifi_only")

    /** Descargar solo con Wi-Fi. Default true. */
    val dlWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[dlWifiOnlyKey] ?: true }

    suspend fun setDlWifiOnly(value: Boolean) {
        context.dataStore.edit { it[dlWifiOnlyKey] = value }
    }

    // ---------------- Apariencia ----------------

    private val themeKey = stringPreferencesKey("theme")

    /** Tema de la app ("sistema" | "oscuro" | "claro"). Default "sistema". */
    val theme: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "sistema" }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[themeKey] = value }
    }

    private val languageKey = stringPreferencesKey("language")

    /** Idioma de la UI. Default "es" (por ahora solo español). */
    val language: Flow<String> = context.dataStore.data.map { it[languageKey] ?: "es" }

    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[languageKey] = value }
    }

    private val defaultSortKey = stringPreferencesKey("default_sort")

    /**
     * Orden global del catálogo ("nombre" | "recientes" | "rating" | "anio").
     * Al cambiarse se aplica a sort_vod y sort_series. Default "nombre".
     */
    val defaultSort: Flow<String> = context.dataStore.data.map { it[defaultSortKey] ?: "nombre" }

    suspend fun setDefaultSort(value: String) {
        context.dataStore.edit {
            it[defaultSortKey] = value
            it[sortVodKey] = value
            it[sortSeriesKey] = value
        }
    }

    // ---------------- Red ----------------

    private val privateDnsKey = booleanPreferencesKey("private_dns")

    /**
     * DNS privado (DNS-over-HTTPS contra Cloudflare 1.1.1.1). Apagado por defecto.
     * Se aplica al OkHttpClient de Xtream, TMDB y el test de velocidad.
     */
    val privateDns: Flow<Boolean> = context.dataStore.data.map { it[privateDnsKey] ?: false }

    suspend fun setPrivateDns(value: Boolean) {
        context.dataStore.edit { it[privateDnsKey] = value }
    }

    private val lastSpeedMbpsKey = floatPreferencesKey("last_speed_mbps")
    private val lastSpeedAtKey = longPreferencesKey("last_speed_at")
    private val lastSpeedDnsKey = stringPreferencesKey("last_speed_dns")

    /** Última medición del test de velocidad (-1 si nunca se midió). */
    val lastSpeedMbps: Flow<Float> = context.dataStore.data.map { it[lastSpeedMbpsKey] ?: -1f }
    val lastSpeedAt: Flow<Long> = context.dataStore.data.map { it[lastSpeedAtKey] ?: 0L }

    /** "Cloudflare 1.1.1.1" o "sistema": con qué DNS se hizo la última medición. */
    val lastSpeedDns: Flow<String> = context.dataStore.data.map { it[lastSpeedDnsKey] ?: "sistema" }

    suspend fun setLastSpeed(mbps: Float, dnsLabel: String) {
        context.dataStore.edit {
            it[lastSpeedMbpsKey] = mbps
            it[lastSpeedAtKey] = System.currentTimeMillis()
            it[lastSpeedDnsKey] = dnsLabel
        }
    }
}
