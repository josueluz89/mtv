package com.mtv.iptv.util

/** 754000 ms -> "12:34"; 3723000 ms -> "1:02:03". */
fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** 0.75 -> "75%", para mostrar progreso. */
fun formatProgress(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0) return ""
    val pct = ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100)
    return "$pct%"
}
