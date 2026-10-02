package com.mtv.iptv.data.remote

import com.mtv.iptv.data.remote.xtream.XtreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Test de velocidad contra el servidor Xtream activo.
 *
 * Descarga hasta 10 MB (o 12 s) de un stream real del servidor (primera
 * película disponible, si no un canal en vivo) usando el OkHttpClient del
 * [HttpClientProvider], así que RESPETA el ajuste de DNS privado: la medición
 * se hace con el DNS que esté activo.
 */
class SpeedTest(
    private val httpProvider: HttpClientProvider,
    private val repo: XtreamRepository,
) {

    data class Result(
        /** Megabits por segundo. */
        val mbps: Double,
        /** "Cloudflare 1.1.1.1" o "sistema": DNS usado en la medición. */
        val dnsLabel: String,
        /** Veredicto en español: SD / HD / 4K / baja. */
        val verdict: String,
    )

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        const val MAX_MS = 12_000L

        fun verdictFor(mbps: Double): String = when {
            mbps >= 25 -> "Suficiente para 4K"
            mbps >= 8 -> "Suficiente para HD"
            mbps >= 3 -> "Suficiente para SD"
            else -> "Velocidad baja: puede haber cortes"
        }
    }

    /** Elige un stream real del servidor para medir. Requiere sesión activa. */
    private suspend fun pickUrl(): String {
        val vods = repo.getVodStreams()
        val vod = vods.firstOrNull { it.streamId != 0 }
        if (vod != null) return repo.vodUrl(vod.streamId, vod.containerExtension)
        val live = repo.getLiveStreams().firstOrNull { it.streamId != 0 }
        if (live != null) return repo.liveUrl(live.streamId)
        error("No hay contenido disponible para probar")
    }

    suspend fun run(): Result = withContext(Dispatchers.IO) {
        val url = pickUrl()
        val dnsLabel = httpProvider.dnsLabel()
        // Cliente dedicado con timeouts propios, pero con el DNS del proveedor.
        val client = httpProvider.client().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MTV/1.1 (speedtest)")
            .build()

        var bytes = 0L
        val start = android.os.SystemClock.elapsedRealtime()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("El servidor respondió ${resp.code}")
            val body = resp.body ?: error("Respuesta sin contenido")
            val buf = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    bytes += n
                    val elapsed = android.os.SystemClock.elapsedRealtime() - start
                    if (bytes >= MAX_BYTES || elapsed >= MAX_MS) break
                }
            }
        }
        val elapsedMs = (android.os.SystemClock.elapsedRealtime() - start).coerceAtLeast(1L)
        if (bytes <= 0) error("No se pudo descargar nada")
        val mbps = bytes * 8.0 / elapsedMs / 1000.0
        Result(mbps = mbps, dnsLabel = dnsLabel, verdict = verdictFor(mbps))
    }
}
