package com.mtv.iptv.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.memory.MemoryCache
import com.mtv.iptv.data.local.db.MtvDatabase
import com.mtv.iptv.data.local.prefs.SecurePrefs
import com.mtv.iptv.data.local.prefs.UserPrefs
import com.mtv.iptv.data.remote.HttpClientProvider
import com.mtv.iptv.data.remote.SpeedTest
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbRepository
import com.mtv.iptv.data.remote.xtream.XtreamClient
import com.mtv.iptv.data.remote.xtream.XtreamRepository
import com.mtv.iptv.data.repository.FavoritesRepository
import com.mtv.iptv.data.repository.PlaybackRepository
import com.mtv.iptv.data.repository.ServerRepository
import com.mtv.iptv.data.repository.SpeedTestHistoryRepository
import com.mtv.iptv.player.PlayerManager
import com.mtv.iptv.player.downloads.DownloadModule
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** DI manual: un solo contenedor por aplicación, sin Hilt. */
class AppContainer(appContext: Context) {

    val database: MtvDatabase by lazy { MtvDatabase.create(appContext) }
    val userPrefs: UserPrefs by lazy { UserPrefs(appContext) }
    val securePrefs: SecurePrefs by lazy { SecurePrefs(appContext) }

    /** Proveedor central de HTTP (aplica el DNS privado cuando está activado). */
    val httpClientProvider = HttpClientProvider()

    val xtreamClient = XtreamClient(httpClientProvider)
    val xtreamRepository = XtreamRepository(
        xtreamClient,
        java.io.File(appContext.filesDir, "catalog"),
    ).also {
        it.eventLog = { msg -> CrashReporter.log(appContext, "xtream", msg) }
    }

    val tmdbClient = TmdbClient(httpClientProvider)
    val tmdbRepository = TmdbRepository(tmdbClient)

    val speedTest by lazy { SpeedTest(httpClientProvider, xtreamRepository) }

    val serverRepository by lazy { ServerRepository(database.serverDao()) }
    val favoritesRepository by lazy { FavoritesRepository(database.favoriteDao()) }
    val playbackRepository by lazy { PlaybackRepository(database.playbackDao()) }

    val speedTestHistoryRepository by lazy {
        SpeedTestHistoryRepository(database.speedTestDao())
    }

    /**
     * Perezoso: antes se instanciaba al crear el AppContainer (arranque de la
     * app), levantando el DownloadManager de Media3, su pool de hilos y el
     * caché en disco aunque el usuario nunca descargue nada. Ahora solo se
     * crea al primer uso (descargas o primera reproducción, que necesita el
     * cacheDataSourceFactory compartido). La firma pública no cambia.
     */
    private val _downloadModule = lazy { DownloadModule(appContext) }
    val downloadModule: DownloadModule by _downloadModule

    /**
     * Delegado privado para poder preguntar si el player ya se creó sin
     * forzarlo (el observador de background no debe inicializarlo).
     */
    private val _playerManager = lazy {
        PlayerManager(appContext, playbackRepository, downloadModule.cacheDataSourceFactory, userPrefs)
    }
    val playerManager: PlayerManager by _playerManager

    /**
     * ImageLoader de Coil (singleton de la app) con caché de memoria
     * ACOTADO: ~10% del heap, con piso de 16 MB y tope de 64 MB, para que
     * los pósters no se coman la RAM en dispositivos chicos (el default de
     * Coil es ~25% del heap). Reutiliza el OkHttpClient compartido, así el
     * toggle de DNS privado también aplica a las imágenes.
     *
     * La UI debe cargar imágenes con [com.mtv.iptv.ui.components.MtvAsyncImage],
     * que además limita el tamaño decodificado con size().
     */
    val imageLoader: ImageLoader by lazy {
        val heap = Runtime.getRuntime().maxMemory()
        val memoryBytes = (heap * 0.10).toLong().coerceIn(
            16L * 1024 * 1024,
            64L * 1024 * 1024,
        )
        ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizeBytes(memoryBytes.toInt())
                    .build()
            }
            .okHttpClient { httpClientProvider.client() }
            .build()
    }

    /**
     * La app pasó a background (ver MtvApplication): guarda la posición de
     * reproducción y libera los decoders/codecs del player con stop()
     * (ExoPlayer.stop() suelta los recursos de los renderers). No llama a
     * release() porque el PlayerManager es un singleton que la UI reutiliza
     * al volver; un release total lo dejaría inservible.
     */
    fun onAppBackgrounded(scope: CoroutineScope) {
        if (!_playerManager.isInitialized()) return
        val pm = _playerManager.value
        scope.launch {
            try {
                pm.savePosition()
            } catch (_: Exception) {
                // Sin posición que guardar (p. ej. nunca se reprodujo nada).
            }
            // stop() toca el player: va en el hilo principal (ver PlayerManager).
            withContext(Dispatchers.Main) { pm.stop() }
        }
    }

    /**
     * Marca de tiempo del último refresco de catálogo. La UI de TV la
     * observa para recargar sus listas cuando el refresco (manual,
     * al volver a primer plano o del Worker) invalida el caché.
     */
    val catalogRefreshTick = MutableStateFlow(0L)

    /**
     * Refresca el catálogo en silencio si pasó la frecuencia configurada
     * desde el último refresco. No hace nada sin sesión activa o en modo
     * "manual". Nunca lanza.
     */
    suspend fun refreshCatalogIfStale() {
        if (xtreamRepository.session == null) return
        val freq = try {
            userPrefs.catalogFreq.first()
        } catch (_: Exception) {
            "12h"
        }
        if (freq == "manual") return
        val last = try {
            userPrefs.getLastCatalogRefresh()
        } catch (_: Exception) {
            0L
        }
        if (System.currentTimeMillis() - last < com.mtv.iptv.work.CatalogWork.freqMillis(freq)) return
        try {
            if (xtreamRepository.forceRefreshCatalog()) {
                val now = System.currentTimeMillis()
                userPrefs.setLastCatalogRefresh(now)
                catalogRefreshTick.value = now
            }
        } catch (_: Exception) {
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no provisto")
}
