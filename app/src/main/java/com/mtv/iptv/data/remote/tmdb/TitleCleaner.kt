package com.mtv.iptv.data.remote.tmdb

data class CleanedTitle(val title: String, val year: Int?)

/**
 * Limpia los títulos tal como vienen de los paneles Xtream para poder
 * buscarlos en TMDB: quita etiquetas tipo [4K], (Latino), "FHD", etc.
 * y extrae el año cuando viene entre paréntesis.
 */
object TitleCleaner {

    private val yearRegex = Regex("""\((19\d{2}|20\d{2})\)""")
    private val bracketRegex = Regex("""[\[\(\{][^\[\]\(\)\{\}]*[\]\)\}]""")
    private val anyYearRegex = Regex("""(19\d{2}|20\d{2})""")

    private val keywords = setOf(
        "4k", "uhd", "2160p", "1080p", "720p", "480p", "360p",
        "fhd", "hd", "hdts", "hdcam", "hd-cam", "hdtv", "web-dl", "webdl", "bluray",
        "cam", "ts", "tc",
        "latino", "lat", "castellano", "cast", "dual", "multi", "audio",
        "subtitulada", "subtitulado", "subt", "sub",
        "espanol", "español", "esp", "mx", "mexico", "méxico",
    )

    fun clean(raw: String): CleanedTitle {
        var text = raw
        var year: Int? = null

        yearRegex.find(text)?.let {
            year = it.groupValues[1].toIntOrNull()
            text = text.replace(it.value, " ")
        }

        // Quita segmentos entre [] () {} (repite por si hay anidados)
        var prev: String
        do {
            prev = text
            text = text.replace(bracketRegex, " ")
        } while (text != prev)

        // Quita palabras clave sueltas (4K, FHD, Latino, ...)
        val words = text.split(Regex("\\s+")).filter { w ->
            val core = w.lowercase().trim('.', ',', ':', ';', '-', '_', '|')
            w.isNotBlank() && core !in keywords
        }

        text = words.joinToString(" ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('-', '|', ':', '.', '–', '—')
            .trim()

        return CleanedTitle(title = text.ifBlank { raw.trim() }, year = year)
    }

    fun yearFromDate(date: String): Int? =
        anyYearRegex.find(date)?.groupValues?.get(1)?.toIntOrNull()
}
