package com.mtv.iptv.player

/**
 * Política de recuperación de reproducción VOD (lógica pura, sin Android).
 *
 * Causa raíz del "a veces carga, a veces no": cualquier fallo al cargar el
 * stream (timeout, 403 por sesión vencida o límite de conexiones del panel,
 * 404, corte de red) dejaba al ExoPlayer en STATE_IDLE con pantalla negra
 * silenciosa — sin reintentos, sin re-login y sin mensaje. Esta política
 * decide, ante cada fallo, si reintentar con backoff ([TRANSIENT]), revalidar
 * la sesión una vez ([AUTH]) o rendirse con mensaje ([NOT_FOUND],
 * [UNSUPPORTED], [FATAL]).
 *
 * [PlayerManager] la aplica; la UI (overlay) solo observa
 * [PlayerManager.isRecovering], [PlayerManager.recoverAttempt] y
 * [PlayerManager.playbackError].
 */

/** Clasificación de un fallo de reproducción. */
enum class FailureKind {
    /** Reintentable con backoff: timeout, corte de red, 5xx, 408, 429. */
    TRANSIENT,

    /** 401/403: la sesión puede estar vencida → re-login silencioso una vez. */
    AUTH,

    /** 404/410: el título no existe (más) en el servidor. No reintentar. */
    NOT_FOUND,

    /** Contenedor o códec que el reproductor no soporta. No reintentar. */
    UNSUPPORTED,

    /** Cualquier otro fallo definitivo. No reintentar. */
    FATAL,
}

/**
 * Fallo normalizado (lo construye [PlayerManager] desde el PlaybackException
 * de Media3; esta clase no conoce tipos de Media3 para seguir siendo
 * testeable en JVM).
 */
data class PlaybackFailure(
    /** Código HTTP del servidor, si el fallo vino de una respuesta HTTP. */
    val httpCode: Int? = null,
    /** Hubo un error de E/S (timeout, conexión cortada, DNS…). */
    val ioError: Boolean = false,
    /** El contenedor o códec no es soportado. */
    val unsupportedFormat: Boolean = false,
)

/** Número de reintentos automáticos ante fallos transitorios. */
const val MAX_AUTO_RETRIES = 3

/**
 * Espera antes del intento [attempt] (1-based): 1s, 2s, 4s, 8s…
 * Backoff exponencial con tope implícito en [MAX_AUTO_RETRIES].
 */
fun retryDelayMs(attempt: Int): Long = when (attempt) {
    1 -> 1_000L
    2 -> 2_000L
    3 -> 4_000L
    else -> 8_000L
}

/** Clasifica un fallo según [PlaybackFailure] (orden: formato → HTTP → E/S). */
fun classifyPlaybackFailure(f: PlaybackFailure): FailureKind {
    if (f.unsupportedFormat) return FailureKind.UNSUPPORTED
    val code = f.httpCode
    if (code != null) {
        return when (code) {
            401, 403 -> FailureKind.AUTH
            404, 410 -> FailureKind.NOT_FOUND
            408, 429 -> FailureKind.TRANSIENT
            in 500..599 -> FailureKind.TRANSIENT
            in 400..499 -> FailureKind.FATAL
            else -> FailureKind.TRANSIENT
        }
    }
    return if (f.ioError) FailureKind.TRANSIENT else FailureKind.FATAL
}
