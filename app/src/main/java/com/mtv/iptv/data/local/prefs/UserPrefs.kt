package com.mtv.iptv.data.local.prefs

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

    private val lastCatalogRefreshKey = longPreferencesKey("last_catalog_refresh")

    /**
     * Marca de tiempo del último refresco automático del catálogo.
     * La usan el Worker periódico y el refresco al volver a primer plano
     * para no descargar de más.
     */
    suspend fun getLastCatalogRefresh(): Long =
        context.dataStore.data.map { it[lastCatalogRefreshKey] ?: 0L }.first()

    suspend fun setLastCatalogRefresh(value: Long) {
        context.dataStore.edit { it[lastCatalogRefreshKey] = value }
    }

    private val catalogFreqKey = stringPreferencesKey("catalog_refresh_freq")
    private val catalogBackgroundKey = booleanPreferencesKey("catalog_background")

    /**
     * Frecuencia de actualización del catálogo: "6h" | "12h" | "24h" | "manual".
     * "manual" = solo con el botón "Actualizar ahora".
     */
    val catalogFreq: Flow<String> =
        context.dataStore.data.map { it[catalogFreqKey] ?: "12h" }

    /** Si la actualización en segundo plano (Worker periódico) está activa. */
    val catalogBackground: Flow<Boolean> =
        context.dataStore.data.map { it[catalogBackgroundKey] ?: true }

    suspend fun setCatalogFreq(value: String) {
        context.dataStore.edit { it[catalogFreqKey] = value }
    }

    suspend fun setCatalogBackground(value: Boolean) {
        context.dataStore.edit { it[catalogBackgroundKey] = value }
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

    private val liveTvOrderKey = stringPreferencesKey("live_tv_order")

    /**
     * Orden de los canales en TV en vivo: "proveedor" (por número de canal
     * del proveedor) | "alfabetico". Default "proveedor".
     */
    val liveTvOrder: Flow<String> =
        context.dataStore.data.map { it[liveTvOrderKey] ?: "proveedor" }

    suspend fun setLiveTvOrder(value: String) {
        context.dataStore.edit { it[liveTvOrderKey] = value }
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

    private val subtitleSizeKey = stringPreferencesKey("subtitle_size")

    /** Tamaño del texto de subtítulos ("S" | "M" | "L"). Default "M". */
    val subtitleSize: Flow<String> =
        context.dataStore.data.map { it[subtitleSizeKey] ?: "M" }

    suspend fun setSubtitleSize(value: String) {
        context.dataStore.edit { it[subtitleSizeKey] = value }
    }

    private val subtitleBackgroundKey = stringPreferencesKey("subtitle_background")

    /**
     * Fondo de los subtítulos: "solido" (recuadro negro) | "semi"
     * (semitransparente) | "ninguno" (sin recuadro, con sombra).
     * Default "semi".
     */
    val subtitleBackground: Flow<String> =
        context.dataStore.data.map { it[subtitleBackgroundKey] ?: "semi" }

    suspend fun setSubtitleBackground(value: String) {
        context.dataStore.edit { it[subtitleBackgroundKey] = value }
    }

    private val subtitleColorKey = stringPreferencesKey("subtitle_color")

    /** Color del texto de subtítulos: "blanco" | "amarillo" | "cian" | "verde". Default "blanco". */
    val subtitleColor: Flow<String> =
        context.dataStore.data.map { it[subtitleColorKey] ?: "blanco" }

    suspend fun setSubtitleColor(value: String) {
        context.dataStore.edit { it[subtitleColorKey] = value }
    }

    private val openSubtitlesKeyKey = stringPreferencesKey("opensubtitles_key")

    /**
     * API key de OpenSubtitles (gratuita en opensubtitles.com). Se usa para
     * buscar/descargar subtítulos por TMDB ID cuando el video no trae.
     * Default "" (sin configurar).
     */
    val openSubtitlesKey: Flow<String> =
        context.dataStore.data.map { it[openSubtitlesKeyKey] ?: "" }

    suspend fun setOpenSubtitlesKey(value: String) {
        context.dataStore.edit { it[openSubtitlesKeyKey] = value }
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

    // ---------------- Decodificación / reproductor avanzado (v1.9.0) ----------------

    private val videoDecoderKey = stringPreferencesKey("video_decoder")

    /** Decodificador de video: "hw" (hardware) | "sw" (software). Default "hw". */
    val videoDecoder: Flow<String> = context.dataStore.data.map { it[videoDecoderKey] ?: "hw" }

    suspend fun setVideoDecoder(value: String) {
        context.dataStore.edit { it[videoDecoderKey] = value }
    }

    private val audioDecoderKey = stringPreferencesKey("audio_decoder")

    /**
     * Decodificador de audio: "auto" (hardware + FFmpeg de respaldo) |
     * "hw" (solo hardware, sin FFmpeg) | "sw" (preferir FFmpeg/software).
     * Default "auto".
     */
    val audioDecoder: Flow<String> = context.dataStore.data.map { it[audioDecoderKey] ?: "auto" }

    suspend fun setAudioDecoder(value: String) {
        context.dataStore.edit { it[audioDecoderKey] = value }
    }

    private val bufferSizeKey = stringPreferencesKey("buffer_size")

    /** Tamaño del buffer de reproducción: "pequeno" | "medio" | "grande". Default "medio". */
    val bufferSize: Flow<String> = context.dataStore.data.map { it[bufferSizeKey] ?: "medio" }

    suspend fun setBufferSize(value: String) {
        context.dataStore.edit { it[bufferSizeKey] = value }
    }

    private val afrKey = booleanPreferencesKey("afr_enabled")

    /**
     * Auto frame rate: intenta ajustar la tasa de refresco de la pantalla
     * a los fps del video. Default false.
     */
    val afrEnabled: Flow<Boolean> = context.dataStore.data.map { it[afrKey] ?: false }

    suspend fun setAfrEnabled(value: Boolean) {
        context.dataStore.edit { it[afrKey] = value }
    }

    private val aspectRatioKey = stringPreferencesKey("aspect_ratio")

    /** Aspecto del video: "fit" (ajustar) | "fill" (llenar) | "zoom". Default "fit". */
    val aspectRatio: Flow<String> = context.dataStore.data.map { it[aspectRatioKey] ?: "fit" }

    suspend fun setAspectRatio(value: String) {
        context.dataStore.edit { it[aspectRatioKey] = value }
    }

    private val surroundDefaultKey = booleanPreferencesKey("surround_default")

    /** Elegir pista de audio envolvente por defecto cuando exista. Default false. */
    val surroundDefault: Flow<Boolean> = context.dataStore.data.map { it[surroundDefaultKey] ?: false }

    suspend fun setSurroundDefault(value: Boolean) {
        context.dataStore.edit { it[surroundDefaultKey] = value }
    }

    // ---------------- General (v1.9.0, estilo TiviMate) ----------------

    private val openOnBootKey = booleanPreferencesKey("open_on_boot")

    /** Abrir la app al encender el dispositivo. Default false. */
    val openOnBoot: Flow<Boolean> = context.dataStore.data.map { it[openOnBootKey] ?: false }

    suspend fun setOpenOnBoot(value: Boolean) {
        context.dataStore.edit { it[openOnBootKey] = value }
    }

    private val openLastChannelKey = booleanPreferencesKey("open_last_channel")

    /** Abrir el último canal reproducido al abrir la app. Default false. */
    val openLastChannel: Flow<Boolean> = context.dataStore.data.map { it[openLastChannelKey] ?: false }

    suspend fun setOpenLastChannel(value: Boolean) {
        context.dataStore.edit { it[openLastChannelKey] = value }
    }

    private val lastLiveChannelKey = stringPreferencesKey("last_live_channel")

    /** streamId del último canal en vivo reproducido (para "abrir el último canal"). */
    val lastLiveChannel: Flow<String> = context.dataStore.data.map { it[lastLiveChannelKey] ?: "" }

    suspend fun setLastLiveChannel(value: String) {
        context.dataStore.edit { it[lastLiveChannelKey] = value }
    }

    private val pipOnHomeKey = booleanPreferencesKey("pip_on_home")

    /** Cambiar a picture-in-picture al pulsar Home durante la reproducción. Default false. */
    val pipOnHome: Flow<Boolean> = context.dataStore.data.map { it[pipOnHomeKey] ?: false }

    suspend fun setPipOnHome(value: Boolean) {
        context.dataStore.edit { it[pipOnHomeKey] = value }
    }

    private val confirmExitKey = booleanPreferencesKey("confirm_exit")

    /** Pedir confirmación (pulsar Atrás dos veces) para salir de la app. Default true. */
    val confirmExit: Flow<Boolean> = context.dataStore.data.map { it[confirmExitKey] ?: true }

    suspend fun setConfirmExit(value: Boolean) {
        context.dataStore.edit { it[confirmExitKey] = value }
    }

    private val userAgentKey = stringPreferencesKey("user_agent")

    /** User-Agent personalizado para las peticiones de red. Vacío = el de la app. */
    val userAgent: Flow<String> = context.dataStore.data.map { it[userAgentKey] ?: "" }

    suspend fun setUserAgent(value: String) {
        context.dataStore.edit { it[userAgentKey] = value }
    }

    // ---------------- Ordenación (v1.9.0) ----------------

    private val vodSortModeKey = stringPreferencesKey("vod_sort_mode")
    private val seriesSortModeKey = stringPreferencesKey("series_sort_mode")

    /**
     * Modo de orden: "lista" (orden del proveedor) | "nombre" |
     * "rating" (calificación) | "fecha" (fecha de agregado). Default "lista".
     */
    val vodSortMode: Flow<String> = context.dataStore.data.map { it[vodSortModeKey] ?: "lista" }
    val seriesSortMode: Flow<String> = context.dataStore.data.map { it[seriesSortModeKey] ?: "lista" }

    suspend fun setVodSortMode(value: String) {
        context.dataStore.edit { it[vodSortModeKey] = value }
    }

    suspend fun setSeriesSortMode(value: String) {
        context.dataStore.edit { it[seriesSortModeKey] = value }
    }

    private val groupByCategoryKey = booleanPreferencesKey("group_by_category")

    /** Agrupar "Todas las películas/shows" por categorías. Default false. */
    val groupByCategory: Flow<Boolean> = context.dataStore.data.map { it[groupByCategoryKey] ?: false }

    suspend fun setGroupByCategory(value: Boolean) {
        context.dataStore.edit { it[groupByCategoryKey] = value }
    }

    // ---------------- Control parental (v1.9.0) ----------------

    private val parentalPinKey = stringPreferencesKey("parental_pin")

    /** PIN de control parental. Vacío = desactivado. */
    val parentalPin: Flow<String> = context.dataStore.data.map { it[parentalPinKey] ?: "" }

    suspend fun setParentalPin(value: String) {
        context.dataStore.edit { it[parentalPinKey] = value }
    }

    private val lockTvKey = booleanPreferencesKey("lock_tv")
    private val lockMoviesKey = booleanPreferencesKey("lock_movies")
    private val lockSeriesKey = booleanPreferencesKey("lock_series")

    /** Secciones bloqueadas con PIN. Default false. */
    val lockTv: Flow<Boolean> = context.dataStore.data.map { it[lockTvKey] ?: false }
    val lockMovies: Flow<Boolean> = context.dataStore.data.map { it[lockMoviesKey] ?: false }
    val lockSeries: Flow<Boolean> = context.dataStore.data.map { it[lockSeriesKey] ?: false }

    suspend fun setLockTv(value: Boolean) {
        context.dataStore.edit { it[lockTvKey] = value }
    }

    suspend fun setLockMovies(value: Boolean) {
        context.dataStore.edit { it[lockMoviesKey] = value }
    }

    suspend fun setLockSeries(value: Boolean) {
        context.dataStore.edit { it[lockSeriesKey] = value }
    }

    // ---------------- Mando a distancia (v1.9.0) ----------------

    private val keyChannelUpKey = intPreferencesKey("key_channel_up")
    private val keyChannelDownKey = intPreferencesKey("key_channel_down")
    private val keyGuideKey = intPreferencesKey("key_guide")
    private val keyInfoKey = intPreferencesKey("key_info")

    /** Keycodes configurables del mando (captura de tecla en Ajustes → Mando). */
    val keyChannelUp: Flow<Int> =
        context.dataStore.data.map { it[keyChannelUpKey] ?: KeyEvent.KEYCODE_CHANNEL_UP }
    val keyChannelDown: Flow<Int> =
        context.dataStore.data.map { it[keyChannelDownKey] ?: KeyEvent.KEYCODE_CHANNEL_DOWN }
    val keyGuide: Flow<Int> =
        context.dataStore.data.map { it[keyGuideKey] ?: KeyEvent.KEYCODE_GUIDE }
    val keyInfo: Flow<Int> =
        context.dataStore.data.map { it[keyInfoKey] ?: KeyEvent.KEYCODE_INFO }

    suspend fun setKeyChannelUp(value: Int) {
        context.dataStore.edit { it[keyChannelUpKey] = value }
    }

    suspend fun setKeyChannelDown(value: Int) {
        context.dataStore.edit { it[keyChannelDownKey] = value }
    }

    suspend fun setKeyGuide(value: Int) {
        context.dataStore.edit { it[keyGuideKey] = value }
    }

    suspend fun setKeyInfo(value: Int) {
        context.dataStore.edit { it[keyInfoKey] = value }
    }
}
