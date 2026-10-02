package com.mtv.iptv.player.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Calidad elegida por el usuario para descargas adaptativas (HLS/DASH). */
enum class DownloadQuality { AUTOMATICA, ALTA, MEDIA, BAJA }

/** Estado de una descarga para la UI. */
sealed interface EstadoDescarga {
    data object NoDescargado : EstadoDescarga
    data class Descargando(val progreso: Int) : EstadoDescarga
    data object Descargado : EstadoDescarga
    data object Error : EstadoDescarga
}

/** Una descarga registrada en el índice, con metadatos para mostrarla. */
data class DownloadEntry(
    val id: String,
    val title: String,
    val imageUrl: String,
    val url: String,
    val estado: EstadoDescarga,
    val bytesDescargados: Long,
    val tamanoTotal: Long,
)

private const val MAX_CACHE_BYTES = 5L * 1024 * 1024 * 1024 // 5 GB
private const val USER_AGENT = "MTV/1.1"

/**
 * Módulo de descargas offline (Media3).
 *
 * - [cache]: SimpleCache compartido ("mtv-downloads", evictor LRU de 5 GB).
 * - [cacheDataSourceFactory]: CacheDataSource.Factory COMPARTIDO. PlayerManager
 *   lo usa en su DefaultMediaSourceFactory, así la reproducción lee del caché
 *   automáticamente (offline transparente).
 * - [downloadManager]: DownloadManager de Media3 (lo usa MtvDownloadService).
 * - [tracker]: estados de descarga como StateFlow para la UI.
 */
class DownloadModule(appContext: Context) {

    private val context = appContext.applicationContext
    private val databaseProvider by lazy { StandaloneDatabaseProvider(context) }

    val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "mtv-downloads"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            databaseProvider,
        )
    }

    /** Factory COMPARTIDA: descargas y reproducción leen/escriben el mismo caché. */
    val cacheDataSourceFactory: CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
            )

    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            cache,
            DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT),
            Executors.newFixedThreadPool(3),
        )

    val tracker = DownloadTracker(downloadManager)

    /** Bytes ocupados actualmente en el caché de descargas. */
    fun usedSpaceBytes(): Long = try {
        cache.cacheSpace
    } catch (e: Exception) {
        0L
    }

    /**
     * Inicia una descarga. El id del request es el [mediaKey] (ej. "vod:123").
     * Para HLS/DASH usa DownloadHelper con el límite de calidad elegido;
     * para mp4 progresivo descarga directa.
     *
     * Debe llamarse desde un hilo con Looper (UI).
     */
    suspend fun startDownload(
        url: String,
        id: String,
        title: String,
        imageUrl: String,
        quality: DownloadQuality = DownloadQuality.AUTOMATICA,
    ): Boolean {
        return try {
            val data = encodeData(title, imageUrl, url)
            val request = if (isAdaptiveUrl(url)) {
                prepareAdaptiveRequest(url, id, data, quality)
            } else {
                DownloadRequest.Builder(id, Uri.parse(url))
                    .setData(data)
                    .build()
            }
            DownloadService.sendAddDownload(
                context, MtvDownloadService::class.java, request, /* foreground= */ true
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Borra una descarga (y sus datos del caché). */
    fun removeDownload(id: String) {
        DownloadService.sendRemoveDownload(
            context, MtvDownloadService::class.java, id, /* foreground= */ false
        )
    }

    private fun isAdaptiveUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd")
    }

    private suspend fun prepareAdaptiveRequest(
        url: String,
        id: String,
        data: ByteArray,
        quality: DownloadQuality,
    ): DownloadRequest = suspendCancellableCoroutine { cont ->
        val paramsBuilder = DefaultTrackSelector.Parameters.Builder(context)
        when (quality) {
            DownloadQuality.ALTA -> paramsBuilder.setMaxVideoSize(1920, 1080)
            DownloadQuality.MEDIA -> paramsBuilder.setMaxVideoSize(1280, 720)
            DownloadQuality.BAJA -> paramsBuilder.setMaxVideoSize(854, 480)
            DownloadQuality.AUTOMATICA -> { /* sin límite */ }
        }
        val mime = if (url.lowercase().contains(".mpd")) {
            MimeTypes.APPLICATION_MPD
        } else {
            MimeTypes.APPLICATION_M3U8
        }
        val mediaItem = MediaItem.Builder().setUri(url).setMimeType(mime).build()
        // dataSourceFactory es obligatorio para HLS/DASH; se usa el caché compartido.
        val helper = DownloadHelper.forMediaItem(
            mediaItem, paramsBuilder.build(), null, cacheDataSourceFactory
        )
        cont.invokeOnCancellation { helper.release() }
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(h: DownloadHelper) {
                try {
                    cont.resume(h.getDownloadRequest(id, data))
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                } finally {
                    h.release()
                }
            }

            override fun onPrepareError(h: DownloadHelper, e: IOException) {
                h.release()
                cont.resumeWithException(e)
            }
        })
    }

    companion object {
        fun encodeData(title: String, imageUrl: String, url: String): ByteArray =
            "$title\n$imageUrl\n$url".toByteArray()

        fun decodeData(data: ByteArray?): Triple<String, String, String> {
            if (data == null) return Triple("", "", "")
            val parts = String(data).split("\n", limit = 3)
            return Triple(
                parts.getOrElse(0) { "" },
                parts.getOrElse(1) { "" },
                parts.getOrElse(2) { "" },
            )
        }
    }
}

/**
 * Observa el [DownloadManager] y expone el estado de cada descarga
 * (por id = mediaKey) como StateFlow para la UI.
 */
class DownloadTracker(private val downloadManager: DownloadManager) {

    private val _estados = MutableStateFlow<Map<String, EstadoDescarga>>(emptyMap())
    val estados: StateFlow<Map<String, EstadoDescarga>> = _estados

    private val _entradas = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entradas: StateFlow<List<DownloadEntry>> = _entradas

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(manager: DownloadManager) = refresh()
        override fun onDownloadChanged(
            manager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) = refresh()

        override fun onDownloadRemoved(manager: DownloadManager, download: Download) = refresh()
    }

    init {
        downloadManager.addListener(listener)
        refresh()
    }

    /** Flow con el estado de una descarga por su id (mediaKey). */
    fun estadoFlow(id: String): Flow<EstadoDescarga> =
        estados.map { it[id] ?: EstadoDescarga.NoDescargado }.distinctUntilChanged()

    private fun refresh() {
        val estados = mutableMapOf<String, EstadoDescarga>()
        val entradas = mutableListOf<DownloadEntry>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        try {
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.state == Download.STATE_REMOVING) continue
                val id = download.request.id
                val estado: EstadoDescarga = when (download.state) {
                    Download.STATE_COMPLETED -> EstadoDescarga.Descargado
                    Download.STATE_FAILED -> EstadoDescarga.Error
                    else -> EstadoDescarga.Descargando(
                        download.percentDownloaded.toInt().coerceIn(0, 100)
                    )
                }
                estados[id] = estado
                val (title, imageUrl, url) = DownloadModule.decodeData(download.request.data)
                entradas += DownloadEntry(
                    id = id,
                    title = title.ifBlank { id },
                    imageUrl = imageUrl,
                    url = url,
                    estado = estado,
                    bytesDescargados = download.bytesDownloaded,
                    tamanoTotal = download.contentLength,
                )
            }
        } catch (e: Exception) {
            // índice no disponible todavía
        } finally {
            cursor.close()
        }
        _estados.value = estados
        _entradas.value = entradas.sortedBy { it.title.lowercase() }
    }
}
