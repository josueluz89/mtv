package com.mtv.iptv.player.vlc

import android.content.Context
import android.view.SurfaceView
import com.mtv.iptv.data.repository.PlaybackRepository

/**
 * Interfaz mínima del motor de reproducción libVLC (v1.3).
 *
 * La UI del reproductor ([ui.player.PlayerScreen], [ui.player.TvPlayerScreen]
 * y [ui.player.TrackDialogs]) programa EXCLUSIVAMENTE contra esta interfaz:
 * no importa nada de `org.videolan.*`, así la UI compila aunque la
 * implementación concreta aún no esté en el árbol.
 *
 * Contrato que debe cumplir la implementación real
 * (`com.mtv.iptv.player.vlc.VlcPlayerManager`, la conecta el integrador):
 * - Constructor público `(Context, PlaybackRepository)` — ver [VlcPlayerFactory].
 * - `attachSurface` debe tolerar el ciclo de vida de la Surface (libVLC
 *   `IVLCVout.attachViews` ya maneja los callbacks del SurfaceHolder).
 * - `attachSurface`/`detachSurface` deben ser idempotentes y seguros de
 *   llamar varias veces seguidas.
 * - Los getters (`positionMs`, `durationMs`, `isPlaying`, `videoInfo()`...)
 *   pueden llamarse desde el hilo UI; libVLC es thread-safe para estas lecturas.
 * - `play` con URL `file://` debe funcionar (descargas locales).
 * - `setRate` acepta 0.5f–2f.
 * - `selectAudioTrack(-1)` = pista automática; `selectSubtitleTrack(-1)` =
 *   subtítulos desactivados.
 * - `savePosition` guarda en [PlaybackRepository] con la misma semántica que
 *   el PlayerManager viejo: si faltan menos de 10 s para el final, guarda 0.
 */
interface VlcPlayer {

    // ---------------- Superficie de video ----------------

    /**
     * Conecta el [SurfaceView] donde libVLC va a renderizar
     * (`mediaPlayer.attachViews(...)` / `IVLCVout` en la implementación).
     */
    fun attachSurface(surfaceView: SurfaceView)

    /** Desconecta la vista de video (libVLC `detachViews`). */
    fun detachSurface()

    // ---------------- Reproducción ----------------

    /**
     * Reproduce [url] (remota http/https o local `file://`) desde
     * [startPositionMs]. Registra [mediaKey]/[title]/[imageUrl] como medio actual.
     */
    fun play(
        url: String,
        mediaKey: String,
        title: String,
        imageUrl: String,
        startPositionMs: Long = 0,
    )

    fun resume()
    fun pause()

    val isPlaying: Boolean

    /** Posición actual en ms (0 si no hay medio). */
    val positionMs: Long

    /** Duración en ms; <= 0 si se desconoce (stream en vivo). */
    val durationMs: Long

    fun seekTo(positionMs: Long)

    /** Velocidad de reproducción (rango válido 0.5f–2f). */
    fun setRate(rate: Float)
    val rate: Float

    /** Detiene la reproducción (libera el medio; NO destruye el player). */
    fun stop()

    /** Libera libVLC por completo. */
    fun release()

    // ---------------- Medio actual ----------------

    val currentMediaKey: String
    val currentTitle: String
    val currentImageUrl: String
    val currentUrl: String

    /** Se invoca (en cualquier hilo) cuando la reproducción llega al final. */
    fun setOnEndedListener(listener: (() -> Unit)?)

    /** Se invoca (en cualquier hilo) ante un error; [message] en español. */
    fun setOnErrorListener(listener: ((message: String) -> Unit)?)

    /**
     * Guarda la posición actual en [PlaybackRepository] ("continuar donde quedó").
     * Suspende: lee posición/duración donde corresponda y escribe en Room en IO.
     */
    suspend fun savePosition()

    // ---------------- Pistas ----------------

    fun audioTracks(): List<VlcTrackOption>
    fun subtitleTracks(): List<VlcTrackOption>

    /** [trackId] = id de libVLC; -1 = pista automática. */
    fun selectAudioTrack(trackId: Int)

    /** [trackId] = id de libVLC; -1 = subtítulos desactivados. */
    fun selectSubtitleTrack(trackId: Int)

    /** Id de la pista de audio activa, o -1 si está en automática. */
    val selectedAudioTrackId: Int

    /** Id del subtítulo activo, o -1 si están desactivados. */
    val selectedSubtitleTrackId: Int

    // ---------------- Presentación ----------------

    fun setAspectMode(mode: VlcAspectMode)

    /**
     * false si el motor no puede seguir renderizando en modo Picture-in-Picture
     * (la UI oculta el botón en ese caso).
     */
    val supportsPip: Boolean

    /** Info del video actual para "Información del stream"; null si no hay. */
    fun videoInfo(): VlcVideoInfo?
}

/** Una pista de audio o subtítulo expuesta por libVLC. */
data class VlcTrackOption(
    /** Id de pista de libVLC (tal cual lo reporta el motor). */
    val trackId: Int,
    val label: String,
)

/** Info del video actual (para "Información del stream"). */
data class VlcVideoInfo(
    val width: Int,
    val height: Int,
    /** Nombre del códec, ej. "H264", "HEVC". Vacío si se desconoce. */
    val codec: String,
    /** Cuadros por segundo; 0 si se desconoce. */
    val fps: Float,
    /** Bitrate en bits/s; 0 si se desconoce. */
    val bitrateBps: Long,
)

enum class VlcAspectMode {
    /** Ajustar sin recortar (letterbox). */
    FIT,
    /** Llenar la pantalla (puede recortar). */
    FILL,
    /** Acercar. */
    ZOOM,
}

/**
 * Crea el [VlcPlayer] real por reflexión para que la UI compile sin la
 * implementación concreta en el árbol. El integrador solo tiene que asegurarse
 * de que `com.mtv.iptv.player.vlc.VlcPlayerManager` exista, implemente
 * [VlcPlayer] y tenga un constructor público `(Context, PlaybackRepository)`.
 */
object VlcPlayerFactory {
    private const val IMPL_CLASS = "com.mtv.iptv.player.vlc.VlcPlayerManager"

    fun create(context: Context, playbackRepository: PlaybackRepository): VlcPlayer {
        val clazz = Class.forName(IMPL_CLASS)
        val ctor = clazz.getConstructor(Context::class.java, PlaybackRepository::class.java)
        return ctor.newInstance(context.applicationContext, playbackRepository) as VlcPlayer
    }
}
