package com.mtv.iptv.data.remote.xtream

/**
 * Constructores puros de URLs de stream Xtream (sin dependencias Android).
 *
 * Viven aquí —y no inline en [XtreamRepository]— para que la construcción de
 * URLs (casos borde incluidos) se pueda probar en JVM sin el SDK de Android.
 * [XtreamRepository.liveUrl]/[vodUrl]/[episodeUrl] delegan en estas funciones.
 */

/** Extensión por defecto cuando el panel no reporta una válida. */
internal const val DEFAULT_CONTAINER_EXTENSION = "mp4"

/**
 * Limpia la extensión de contenedor que reporta el panel ("mp4", ".mkv",
 * " avi ", "mp4?token=…"): recorta espacios, quita un punto inicial y corta
 * cualquier sufijo de query o ruta. Vacía → [DEFAULT_CONTAINER_EXTENSION].
 */
internal fun cleanContainerExtension(raw: String): String =
    raw.trim()
        .trimStart('.')
        .substringBefore('?')
        .substringBefore('/')
        .ifBlank { DEFAULT_CONTAINER_EXTENSION }

/**
 * Extrae la extensión del último segmento de la URL
 * ("…/movie/u/p/123.mkv" → "mkv"). Si no hay extensión visible → "mp4".
 * Se usa para reconstruir la URL tras un re-login silencioso.
 */
internal fun streamExtensionFromUrl(url: String): String {
    val lastSegment = url.substringAfterLast('/').substringBefore('?')
    return lastSegment.substringAfterLast('.', "").ifBlank { DEFAULT_CONTAINER_EXTENSION }
}

internal fun buildLiveUrl(
    baseUrl: String,
    username: String,
    password: String,
    streamId: Int,
): String = "$baseUrl/live/$username/$password/$streamId.ts"

internal fun buildVodUrl(
    baseUrl: String,
    username: String,
    password: String,
    streamId: Int,
    containerExtension: String,
): String =
    "$baseUrl/movie/$username/$password/$streamId.${cleanContainerExtension(containerExtension)}"

internal fun buildEpisodeUrl(
    baseUrl: String,
    username: String,
    password: String,
    episodeId: String,
    containerExtension: String,
): String =
    "$baseUrl/series/$username/$password/$episodeId.${cleanContainerExtension(containerExtension)}"
