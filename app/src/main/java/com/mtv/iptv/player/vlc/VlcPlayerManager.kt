package com.mtv.iptv.player.vlc

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout

/**
 * Estado de reproducción observable por la UI.
 */
enum class VlcPlayerState {
    IDLE, OPENING, BUFFERING, PLAYING, PAUSED, STOPPED, ENDED, ERROR
}

/**
 * Motor de reproducción con libVLC embebido (reemplaza a ExoPlayer/Media3).
 *
 * Reproduce HLS, DASH, TS y mp4/mkv por HTTP/HTTPS con las URLs de Xtream
 * (llevan usuario/clave en el path; no requieren nada especial: libVLC las
 * maneja como URLs HTTP normales).
 *
 * NOTA DE HILOS: los callbacks de libVLC llegan en hilos propios de VLC,
 * nunca en el hilo main. Todo el estado observable se expone vía StateFlow y
 * se actualiza en [Dispatchers.Main]. Las llamadas directas a
 * [mediaPlayer] (time, length, isPlaying, setRate...) son seguras desde
 * cualquier hilo: libVLC es thread-safe para estas operaciones.
 *
 * Salida de video: la UI debe adjuntar su SurfaceView/SurfaceTexture a
 * [vlcVout] (worker de UI):
 * ```
 * manager.vlcVout.setVideoView(surfaceView)
 * manager.vlcVout.setSubtitlesView(subtitleView) // opcional
 * manager.vlcVout.attachViews()
 * ```
 * y llamar a [vlcVout].detachViews() al salir de la pantalla.
 */
class VlcPlayerManager(
    appContext: Context,
    private val playbackRepository: PlaybackRepository,
) : VlcPlayer {

    // Scope principal: publica estado/error desde los eventos de VLC.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val libVlc = LibVLC(
        appContext.applicationContext,
        arrayListOf(
            // Reintentar conexiones HTTP caídas (típico de streams Xtream).
            "--http-reconnect",
            // Búfer de red de 3s: compensa latencia sin demorar el arranque.
            "--network-caching=3000",
        )
    )

    /** Reproductor libVLC. Expuesto para consulta directa thread-safe. */
    val mediaPlayer = MediaPlayer(libVlc)

    /** Salida de video: la UI adjunta aquí sus vistas (ver KDoc de la clase). */
    val vlcVout: IVLCVout get() = mediaPlayer.vlcVout

    private var currentMedia: Media? = null

    // ---- Implementación de VlcPlayer (la UI programa contra la interfaz) ----

    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var surfaceAttached = false

    override fun attachSurface(surfaceView: SurfaceView) {
        if (surfaceAttached) return
        runCatching {
            vlcVout.setVideoView(surfaceView)
            vlcVout.attachViews()
            surfaceAttached = true
        }
    }

    override fun detachSurface() {
        if (!surfaceAttached) return
        runCatching { vlcVout.detachViews() }
        surfaceAttached = false
    }

    override fun setOnEndedListener(listener: (() -> Unit)?) {
        onEndedListener = listener
    }

    override fun setOnErrorListener(listener: ((message: String) -> Unit)?) {
        onErrorListener = listener
    }

    // Se escribe desde play()/stop() (hilo UI) y se lee en el listener de
    // eventos de VLC (hilo propio de libVLC).
    @Volatile
    private var pendingStartPositionMs: Long = 0

    override var currentMediaKey: String = ""
        private set
    override var currentTitle: String = ""
        private set
    override var currentImageUrl: String = ""
        private set
    override var currentUrl: String = ""
        private set

    private val _state = MutableStateFlow(VlcPlayerState.IDLE)
    val state: StateFlow<VlcPlayerState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Último error de reproducción, en español, listo para mostrar en la UI. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    /** Posición actual (se actualiza con los eventos de VLC). */
    val positionMsFlow: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMsFlow: StateFlow<Long> = _durationMs.asStateFlow()

    /** Velocidad actual de reproducción (1f = normal). */
    var playbackSpeed: Float = 1f
        private set

    init {
        mediaPlayer.setEventListener { event ->
            // Llega en el hilo de eventos de libVLC: saltar al main.
            mainScope.launch {
                when (event.type) {
                    MediaPlayer.Event.Opening -> _state.value = VlcPlayerState.OPENING
                    MediaPlayer.Event.Buffering -> {
                        _state.value = VlcPlayerState.BUFFERING
                    }
                    MediaPlayer.Event.Playing -> {
                        _state.value = VlcPlayerState.PLAYING
                        // Continuar donde quedó: setTime solo es efectivo
                        // una vez que el stream ya está reproduciendo.
                        if (pendingStartPositionMs > 0) {
                            runCatching {
                                mediaPlayer.time = pendingStartPositionMs
                            }
                            pendingStartPositionMs = 0
                        }
                    }
                    MediaPlayer.Event.Paused -> _state.value = VlcPlayerState.PAUSED
                    MediaPlayer.Event.Stopped -> _state.value = VlcPlayerState.STOPPED
                    MediaPlayer.Event.EndReached -> {
                        _state.value = VlcPlayerState.ENDED
                        pendingStartPositionMs = 0
                        onEndedListener?.invoke()
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        _state.value = VlcPlayerState.ERROR
                        pendingStartPositionMs = 0
                        val msg =
                            "No se pudo reproducir este contenido. " +
                                "Verificá tu conexión o probá con otro canal/video."
                        _errorMessage.value = msg
                        onErrorListener?.invoke(msg)
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        _positionMs.value =
                            event.timeChanged.takeIf { it >= 0 } ?: 0L
                        _durationMs.value = durationMs
                        if (_state.value == VlcPlayerState.BUFFERING ||
                            _state.value == VlcPlayerState.OPENING
                        ) {
                            _state.value = VlcPlayerState.PLAYING
                        }
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        _durationMs.value =
                            event.lengthChanged.takeIf { it > 0 } ?: 0L
                    }
                    else -> Unit
                }
            }
        }
    }

    // ---------------- Control básico ----------------

    override fun play(
        url: String,
        mediaKey: String,
        title: String,
        imageUrl: String,
        startPositionMs: Long = 0,
    ) {
        currentMediaKey = mediaKey
        currentTitle = title
        currentImageUrl = imageUrl
        currentUrl = url
        mainScope.launch {
            _errorMessage.value = null
            _state.value = VlcPlayerState.OPENING
            _positionMs.value = 0
            _durationMs.value = 0
        }
        pendingStartPositionMs = startPositionMs.coerceAtLeast(0)
        // Detener antes de soltar el Media anterior: así el player suelta su
        // referencia antes de que liberemos la nuestra.
        runCatching { mediaPlayer.stop() }
        releaseCurrentMedia()
        // Las URLs Xtream llevan usuario/clave en el path; se pasan tal cual.
        val media = Media(libVlc, Uri.parse(url))
        currentMedia = media
        mediaPlayer.media = media
        mediaPlayer.play()
    }

    override fun pause() {
        mediaPlayer.pause()
    }

    override fun resume() {
        mediaPlayer.play()
    }

    override fun stop() {
        pendingStartPositionMs = 0
        mediaPlayer.stop()
    }

    override fun seekTo(positionMs: Long) {
        if (!mediaPlayer.isSeekable) return
        val dur = durationMs
        val target = positionMs.coerceAtLeast(0)
            .let { if (dur > 0) it.coerceAtMost(dur) else it }
        mediaPlayer.time = target
        _positionMs.value = target
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    /** Posición actual en ms (lectura directa thread-safe). */
    override val positionMs: Long get() = mediaPlayer.time.takeIf { it >= 0 } ?: 0L

    /** Duración en ms; 0 si se desconoce (TV en vivo). */
    override val durationMs: Long get() = mediaPlayer.length.takeIf { it > 0 } ?: 0L

    override val isPlaying: Boolean get() = mediaPlayer.isPlaying

    /** Velocidad de reproducción (0.5x – 4x típico). */
    override fun setRate(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4f)
        playbackSpeed = clamped
        mediaPlayer.rate = clamped
    }

    override val rate: Float get() = playbackSpeed

    // ---------------- Aspecto / zoom ----------------

    /**
     * Relación de aspecto forzada ("16:9", "4:3", "1:1"...).
     * null = la del video (automático).
     */
    fun setAspectRatio(ratio: String?) {
        mediaPlayer.aspectRatio = ratio
    }

    /**
     * Geometría de recorte/zoom (formato VLC, ej. "16:9", "16:10", "1.85:1").
     * null = sin recorte (automático).
     */
    fun setCropGeometry(geometry: String?) {
        mediaPlayer.cropGeometry = geometry
    }

    /** Escala del video (1f = normal). */
    fun setScale(factor: Float) {
        mediaPlayer.scale = factor
    }

    // ---------------- Pistas de audio / subtítulos ----------------

    override fun audioTracks(): List<VlcTrackOption> =
        mediaPlayer.audioTracks
            ?.filter { it.id >= 0 }
            ?.map { VlcTrackOption(it.id, it.name) }
            ?: emptyList()

    override fun subtitleTracks(): List<VlcTrackOption> =
        mediaPlayer.spuTracks
            ?.filter { it.id >= 0 }
            ?.map { VlcTrackOption(it.id, it.name) }
            ?: emptyList()

    /** Alias: las text tracks de VLC son los subtítulos. */
    fun textTracks(): List<VlcTrackOption> = subtitleTracks()

    override fun selectAudioTrack(trackId: Int) {
        mediaPlayer.audioTrack = trackId
    }

    override fun selectSubtitleTrack(trackId: Int) {
        // -1 = subtítulos desactivados (convención de la UI).
        mediaPlayer.spuTrack = trackId
    }

    override val selectedAudioTrackId: Int get() = mediaPlayer.audioTrack

    override val selectedSubtitleTrackId: Int get() = mediaPlayer.spuTrack

    fun clearError() {
        _errorMessage.value = null
    }

    // ---------------- Continuar donde quedó ----------------

    /**
     * Guarda la posición actual (para "Seguir viendo").
     * Misma lógica que PlayerManager: si faltan menos de 10s, guarda 0.
     * Las lecturas de libVLC son thread-safe; la escritura a Room va en IO.
     */
    override suspend fun savePosition() {
        val pos = positionMs
        val dur = durationMs
        withContext(Dispatchers.IO) {
            val key = currentMediaKey
            if (key.isBlank()) return@withContext
            // Si ya terminó (últimos 10s), guardar 0 para no reanudar al final.
            val positionToSave = if (dur > 0 && pos >= dur - 10_000) 0L else pos
            playbackRepository.save(
                PlaybackEntity(
                    mediaKey = key,
                    name = currentTitle,
                    imageUrl = currentImageUrl,
                    url = currentUrl,
                    positionMs = positionToSave,
                    durationMs = dur,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Posición guardada para [mediaKey], o 0 si no hay. */
    suspend fun savedPositionMs(mediaKey: String): Long =
        withContext(Dispatchers.IO) {
            playbackRepository.get(mediaKey)?.positionMs ?: 0L
        }

    // ---------------- Liberación ----------------

    private fun releaseCurrentMedia() {
        currentMedia?.let { runCatching { it.release() } }
        currentMedia = null
    }

    /**
     * Detiene, suelta las vistas de video, libera el MediaPlayer y el
     * LibVLC nativo. Llamar una sola vez al destruir la app.
     */
    override fun release() {
        runCatching { stop() }
        mediaPlayer.setEventListener(null)
        runCatching { mediaPlayer.detachViews() }
        surfaceAttached = false
        runCatching { mediaPlayer.release() }
        releaseCurrentMedia()
        runCatching { libVlc.release() }
        mainScope.cancel()
    }

    // ---------------- Presentación (VlcPlayer) ----------------

    override fun setAspectMode(mode: VlcAspectMode) {
        when (mode) {
            VlcAspectMode.FIT -> {
                mediaPlayer.aspectRatio = null
                mediaPlayer.cropGeometry = null
            }
            VlcAspectMode.FILL -> {
                mediaPlayer.aspectRatio = "16:9"
                mediaPlayer.cropGeometry = null
            }
            VlcAspectMode.ZOOM -> {
                mediaPlayer.aspectRatio = null
                mediaPlayer.cropGeometry = "16:10"
            }
        }
    }

    override val supportsPip: Boolean = true

    override fun videoInfo(): VlcVideoInfo? {
        if (currentMedia == null) return null
        val codec = runCatching {
            mediaPlayer.videoTracks?.firstOrNull { it.id >= 0 }?.name.orEmpty()
        }.getOrDefault("")
        return VlcVideoInfo(
            width = 0,
            height = 0,
            codec = codec,
            fps = 0f,
            bitrateBps = 0L,
        )
    }
}
