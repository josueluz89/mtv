package com.mtv.iptv.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Pide la duración real de un VOD directamente al servidor.
 *
 * Contexto: parte del contenido Xtream no le entrega la duración a ExoPlayer
 * al inicio (el átomo `moov` del MP4 va al final del archivo y el servidor no
 * anuncia soporte de rangos), así que el reproductor trata el video como
 * "en vivo" y bloquea adelantar/retroceder. Otros reproductores sí pueden
 * porque piden el salto de todas formas.
 *
 * El probe hace exactamente "la petición de duración al servidor":
 * 1. Pide `Range: bytes=0-0` para saber si el servidor honra rangos y el
 *    tamaño total del archivo (cabecera `Content-Range`).
 * 2. Descarga solo los últimos 128 KB y busca el átomo `moov` → `mvhd`
 *    para leer timescale/duración del MP4.
 *
 * Devuelve la duración en ms, o null si el servidor no permite determinarla
 * (en ese caso el seek real tampoco es posible contra ese servidor).
 */
object VodDurationProbe {

    private const val TAIL_BYTES = 131072L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun probe(url: String, userAgent: String): Long? = withContext(Dispatchers.IO) {
        try {
            // 1. ¿Soporta rangos? ¿Tamaño total?
            val totalBytes = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-0")
                .get()
                .build()
                .let { req ->
                    client.newCall(req).execute().use { resp ->
                        if (resp.code != 206) return@withContext null
                        resp.header("Content-Range")
                            ?.substringAfterLast('/', "")
                            ?.toLongOrNull()
                    }
                } ?: return@withContext null
            if (totalBytes <= 0) return@withContext null

            // 2. Cola del archivo → buscar moov/mvhd.
            val from = maxOf(0L, totalBytes - TAIL_BYTES)
            val tail = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=$from-${totalBytes - 1}")
                .get()
                .build()
                .let { req ->
                    client.newCall(req).execute().use { resp ->
                        if (resp.code != 206) return@withContext null
                        resp.body?.bytes()
                    }
                } ?: return@withContext null

            parseMp4Duration(tail)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Busca el marcador "moov" en el fragmento y dentro de él el átomo
     * "mvhd" (movie header) para leer la duración. El corte puede caer a
     * mitad de un átomo, por eso se busca por marcador en vez de parsear
     * cajas desde el inicio del buffer.
     */
    private fun parseMp4Duration(buf: ByteArray): Long? {
        var moovAt = -1
        var i = 0
        while (i + 4 <= buf.size) {
            if (buf[i] == 'm'.code.toByte() && buf[i + 1] == 'o'.code.toByte() &&
                buf[i + 2] == 'o'.code.toByte() && buf[i + 3] == 'v'.code.toByte()
            ) {
                moovAt = i
                break
            }
            i++
        }
        if (moovAt < 0) return null

        // Hijos del moov: [tamaño:4][tipo:4]... buscar "mvhd".
        var off = moovAt + 8
        val end = buf.size
        while (off + 8 <= end) {
            val size = buf.u32(off).toLong()
            if (size < 8) break
            val type = String(buf, off + 4, 4, Charsets.US_ASCII)
            if (type == "mvhd") {
                return parseMvhd(buf, off + 8)
            }
            if (size > (end - off)) break
            off += size.toInt()
        }
        return null
    }

    private fun parseMvhd(buf: ByteArray, p: Int): Long? {
        if (p + 24 > buf.size) return null
        return try {
            val version = buf[p].toInt()
            val (timescale, duration) = if (version == 1) {
                if (p + 36 > buf.size) return null
                buf.u32(p + 20) to buf.u64(p + 28)
            } else {
                buf.u32(p + 12) to buf.u32(p + 16).toLong()
            }
            if (timescale == 0L || duration <= 0) return null
            duration * 1000L / timescale
        } catch (_: Exception) {
            null
        }
    }

    private fun ByteArray.u32(o: Int): Long =
        ((this[o].toInt() and 0xFF).toLong() shl 24) or
            ((this[o + 1].toInt() and 0xFF).toLong() shl 16) or
            ((this[o + 2].toInt() and 0xFF).toLong() shl 8) or
            (this[o + 3].toInt() and 0xFF).toLong()

    private fun ByteArray.u64(o: Int): Long =
        (u32(o) shl 32) or u32(o + 4)
}
