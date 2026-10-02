package com.mtv.iptv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Test de velocidad de la conexión a internet.
 *
 * Descarga cronometrada contra endpoints públicos confiables (CDN), con
 * reintentos y fallback entre varios servidores: el test SIEMPRE se completa
 * con un resultado real mientras haya internet, sin depender del servidor
 * Xtream (que puede estar caído, lento o limitar la descarga) ni de haber
 * iniciado sesión.
 *
 * Usa el OkHttpClient del [HttpClientProvider], así que RESPETA el ajuste de
 * DNS privado: la medición se hace con el DNS que esté activo.
 */
class SpeedTest(
    private val httpProvider: HttpClientProvider,
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
        const val MAX_BYTES = 25L * 1024 * 1024
        const val MAX_MS = 15_000L
        private const val ATTEMPTS_PER_ENDPOINT = 2

        /** (url, bytes a pedir). Se prueban en orden hasta que uno responda. */
        private val ENDPOINTS = listOf(
            // Edge de Cloudflare (cercano) con tamaño a pedido.
            "https://speed.cloudflare.com/__down?bytes=25000000" to 25_000_000L,
            // CDN CacheFly, archivo fijo de 10 MB, muy estable.
            "https://cachefly.cachefly.net/10mb.test" to 10_485_760L,
            // OVH, archivo fijo de 10 MB.
            "https://proof.ovh.net/files/10Mb.dat" to 10_485_760L,
        )

        fun verdictFor(mbps: Double): String = when {
            mbps >= 25 -> "Suficiente para 4K"
            mbps >= 8 -> "Suficiente para HD"
            mbps >= 3 -> "Suficiente para SD"
            else -> "Velocidad baja: puede haber cortes"
        }
    }

    /** Una descarga cronometrada contra [url]. Devuelve Mbps o null si falló. */
    private fun measureOnce(url: String): Double? {
        val client = httpProvider.client().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MTV/1.1 (speedtest)")
            .build()
        return try {
            var bytes = 0L
            val start = android.os.SystemClock.elapsedRealtime()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
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
            if (bytes <= 0) return null
            bytes * 8.0 / elapsedMs / 1000.0
        } catch (_: Exception) {
            null
        }
    }

    suspend fun run(): Result = withContext(Dispatchers.IO) {
        val dnsLabel = httpProvider.dnsLabel()
        var best: Double? = null
        val failures = mutableListOf<String>()
        for ((url, _) in ENDPOINTS) {
            repeat(ATTEMPTS_PER_ENDPOINT) {
                val mbps = measureOnce(url)
                if (mbps != null && mbps > 0) {
                    best = maxOf(best ?: 0.0, mbps)
                    // Con un endpoint bueno basta; el mejor intento representa
                    // la capacidad real de la línea.
                    return@withContext Result(
                        mbps = best!!,
                        dnsLabel = dnsLabel,
                        verdict = verdictFor(best!!),
                    )
                }
            }
            failures.add(url.substringAfter("https://").substringBefore("/"))
        }
        error("Sin conexión a internet (falló en: ${failures.joinToString(", ")})")
    }
}
