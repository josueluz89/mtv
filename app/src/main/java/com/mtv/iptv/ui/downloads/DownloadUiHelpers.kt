package com.mtv.iptv.ui.downloads

import androidx.media3.exoplayer.offline.DownloadManager

/**
 * Helpers de UI para descargas (Media3), compartidos entre la pantalla
 * "Mis descargas" y Ajustes → Datos y sincronización / Descargas.
 */

/** "123456789" -> "117 MB" / "1.2 GB". */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) "%.1f GB".format(gb) else "%d MB".format(bytes / (1024 * 1024))
}

/** Espacio usado = suma de bytes descargados según el índice de descargas. */
fun calcUsedSpace(dm: DownloadManager): Long {
    val cursor = dm.downloadIndex.getDownloads()
    var total = 0L
    try {
        while (cursor.moveToNext()) {
            total += cursor.download.getBytesDownloaded()
        }
    } finally {
        cursor.close()
    }
    return total
}

/** Borra todas las descargas del índice. Devuelve cuántas borró. */
fun removeAllDownloads(dm: DownloadManager): Int {
    val cursor = dm.downloadIndex.getDownloads()
    var n = 0
    try {
        while (cursor.moveToNext()) {
            dm.removeDownload(cursor.download.request.id)
            n++
        }
    } finally {
        cursor.close()
    }
    return n
}
