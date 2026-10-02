package com.mtv.iptv.player

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.local.prefs.UserPrefs
import com.mtv.iptv.data.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Un solo ExoPlayer para toda la app (HLS/DASH/TS progresivo).
 * Guarda y restaura la posición en [PlaybackRepository].
 *
 * AJUSTES DE DECODIFICACIÓN: el player se construye con el triple
 * (videoDecoder, audioDecoder, bufferSize) de [UserPrefs]. Si al llamar a
 * [play] el triple cambió desde la última construcción, el player se
 * reconstruye (se libera el viejo, se crea el nuevo y el llamante re-setea
 * el media item). La UI observa [playerEpoch] para reasignar
 * `playerView.player` y recomponer tras cada reconstrucción.
 *
 * NOTA DE HILOS: ExoPlayer exige que TODO acceso directo a `player`
 * (currentPosition, duration, videoFormat, play, seekTo, currentTracks,
 * setPlaybackSpeed...) ocurra en el hilo principal (el que creó el player).
 * [play], [stop], [audioTracks], [textTracks], [selectAudio],
 * [clearAudioOverride], [selectText] y [disableText] se llaman siempre desde
 * la UI, así que están bien. [savePosition] es suspend y por eso primero lee
 * posición/duración en [Dispatchers.Main] y solo después escribe en Room en
 * [Dispatchers.IO].
 */
class PlayerManager(
    appContext: Context,
    private val playbackRepository: PlaybackRepository,
    cacheDataSourceFactory: CacheDataSource.Factory,
    private val userPrefs: UserPrefs,
) {
    private val appCtx: Context = appContext.applicationContext

    /** Triple que determina cómo se construye el ExoPlayer. */
    private data class BuildConfig(
        val videoDecoder: String, // "hw" | "sw"
        val audioDecoder: String, // "auto" | "hw" | "sw"
        val bufferSize: String,   // "pequeno" | "medio" | "grande"
    ) {
        companion object {
            val DEFAULT = BuildConfig("hw", "auto", "medio")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Último triple leído de las prefs (se mantiene fresco observando los flows). */
    @Volatile
    private var cachedConfig = BuildConfig.DEFAULT

    @Volatile
    private var surroundDefaultCached = false

    init {
        // Diagnóstico: confirma si el respaldo FFmpeg quedó disponible.
        try {
            val available = androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()
            Log.i("PlayerManager", "FFmpeg audio decoder available=$available")
        } catch (_: Throwable) {
            Log.i("PlayerManager", "FFmpeg audio decoder not bundled")
        }
        scope.launch {
            combine(
                userPrefs.videoDecoder,
                userPrefs.audioDecoder,
                userPrefs.bufferSize,
            ) { video, audio, buffer -> BuildConfig(video, audio, buffer) }
                .collect { cachedConfig = it }
        }
        scope.launch {
            userPrefs.surroundDefault.collect { surroundDefaultCached = it }
        }
    }

    private val trackSelector = DefaultTrackSelector(appCtx)
    private val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

    /**
     * Selector que deja pasar SOLO decodificadores de software
     * (OMX.google.* / c2.android.*). Para "hw" se usa el default.
     */
    private fun videoCodecSelector(mode: String): MediaCodecSelector =
        if (mode == "sw") {
            MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder,
                ).filter { info ->
                    val name = info.name.lowercase()
                    name.startsWith("omx.google.") || name.startsWith("c2.android.")
                }
            }
        } else {
            MediaCodecSelector.DEFAULT
        }

    private fun buildPlayer(config: BuildConfig): ExoPlayer {
        // Media3 1.9.0 no expone constructor con MediaCodecSelector: para el
        // modo "sw" se inyecta el selector de solo-software sobreescribiendo
        // buildVideoRenderers. En "hw" va la fábrica estándar.
        val softwareSelector = videoCodecSelector(config.videoDecoder)
        val baseFactory: DefaultRenderersFactory =
            if (config.videoDecoder == "sw") {
                object : DefaultRenderersFactory(appCtx) {
                    // Firma Media3 1.9.0: retorna Unit y agrega los renderers a `out`.
                    override fun buildVideoRenderers(
                        context: Context,
                        extensionRendererMode: Int,
                        mediaCodecSelector: MediaCodecSelector,
                        enableDecoderFallback: Boolean,
                        eventHandler: Handler,
                        eventListener: VideoRendererEventListener,
                        allowedVideoJoiningTimeMs: Long,
                        out: ArrayList<Renderer>,
                    ) {
                        super.buildVideoRenderers(
                            context,
                            extensionRendererMode,
                            softwareSelector,
                            enableDecoderFallback,
                            eventHandler,
                            eventListener,
                            allowedVideoJoiningTimeMs,
                            out,
                        )
                    }
                }
            } else {
                DefaultRenderersFactory(appCtx)
            }
        val renderersFactory = baseFactory
            .setExtensionRendererMode(
                when (config.audioDecoder) {
                    // Solo hardware: FFmpeg fuera.
                    "hw" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    // Preferir FFmpeg/software sobre el hardware.
                    "sw" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    // Hardware primero, FFmpeg como respaldo (comportamiento clásico).
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                },
            )
            .setEnableDecoderFallback(true)

        // (minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
        val (minBuf, maxBuf, forPlayback, afterRebuffer) = when (config.bufferSize) {
            "pequeno" -> intArrayOf(15_000, 30_000, 2_500, 5_000)
            "grande" -> intArrayOf(120_000, 240_000, 5_000, 10_000)
            else -> intArrayOf(50_000, 120_000, 2_500, 5_000) // "medio"
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, forPlayback, afterRebuffer)
            .build()

        Log.i(
            "PlayerManager",
            "Construyendo ExoPlayer video=${config.videoDecoder} " +
                "audio=${config.audioDecoder} buffer=${config.bufferSize}",
        )
        return ExoPlayer.Builder(appCtx, renderersFactory)
            .setTrackSelector(trackSelector)
            // CacheDataSource COMPARTIDO con el módulo de descargas: la reproducción
            // lee automáticamente del caché (offline transparente).
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { attachSurroundListener(it) }
    }

    private var playerActual: ExoPlayer = buildPlayer(BuildConfig.DEFAULT)
    private var builtConfig = BuildConfig.DEFAULT

    /**
     * Getter DINÁMICO: siempre devuelve la instancia vigente. No capturar en
     * un val local si el player puede reconstruirse (ver [playerEpoch]).
     */
    val player: ExoPlayer get() = playerActual

    /**
     * Se incrementa en cada reconstrucción del ExoPlayer. La UI lo observa
     * para reasignar `playerView.player` y recomponer sus lecturas.
     */
    val playerEpoch = MutableStateFlow(0)

    val selector: DefaultTrackSelector get() = trackSelector

    var currentMediaKey: String = ""
        private set
    var currentTitle: String = ""
        private set
    var currentImageUrl: String = ""
        private set
    var currentUrl: String = ""
        private set

    /**
     * Reconstruye el player SOLO si el triple (video, audio, buffer) cambió
     * desde la última construcción. Restaura el medio anterior (si había)
     * para no dejar el player vacío; el llamante ([play]) re-setea su
     * propio media item justo después. Llamar en el hilo principal.
     */
    fun ensurePlayerConfig() {
        val config = cachedConfig
        if (config == builtConfig) return
        Log.i("PlayerManager", "Ajustes cambiaron ($builtConfig -> $config): reconstruyendo player")
        val old = playerActual
        val oldItem = old.currentMediaItem
        val oldPos = old.currentPosition.coerceAtLeast(0L)
        val wasPlaying = old.playWhenReady
        old.release()
        playerActual = buildPlayer(config)
        builtConfig = config
        playerEpoch.value += 1
        if (oldItem != null) {
            playerActual.setMediaItem(oldItem)
            playerActual.prepare()
            if (oldPos > 0) playerActual.seekTo(oldPos)
            playerActual.playWhenReady = wasPlaying
        }
    }

    fun play(url: String, mediaKey: String, title: String, imageUrl: String, startPositionMs: Long = 0) {
        // Aplica decodificadores/buffer vigentes (reconstruye solo si cambiaron).
        ensurePlayerConfig()
        if (mediaKey != currentMediaKey) {
            surroundAppliedKey = ""
            userAudioOverride = false
        }
        currentMediaKey = mediaKey
        currentTitle = title
        currentImageUrl = imageUrl
        currentUrl = url
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.playWhenReady = true
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    /** Guarda la posición actual (para "Seguir viendo"). */
    suspend fun savePosition() {
        // Leer en el hilo main: player.currentPosition/duration fuera del hilo
        // principal lanza IllegalStateException ("Player is accessed on the wrong thread").
        val (pos, dur) = withContext(Dispatchers.Main) {
            player.currentPosition to player.duration
        }
        withContext(Dispatchers.IO) {
            val key = currentMediaKey
            if (key.isBlank()) return@withContext
            val duration = dur.takeIf { it > 0 } ?: 0L
            // Si ya terminó (últimos 10s), guardar 0 para no reanudar al final.
            val positionToSave = if (duration > 0 && pos >= duration - 10_000) 0L else pos
            playbackRepository.save(
                PlaybackEntity(
                    mediaKey = key,
                    name = currentTitle,
                    imageUrl = currentImageUrl,
                    url = currentUrl,
                    positionMs = positionToSave,
                    durationMs = duration,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun release() {
        scope.cancel()
        playerActual.release()
    }

    // ---------------- Audio envolvente por defecto ----------------

    private var surroundAppliedKey = ""
    private var userAudioOverride = false

    /**
     * Si el pref "Audio envolvente por defecto" está activo, al primer
     * onTracksChanged de cada medio (sin override manual de audio) elige la
     * primera pista de audio con 6+ canales, si existe.
     */
    private fun attachSurroundListener(p: ExoPlayer) {
        p.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (!surroundDefaultCached || userAudioOverride) return
                val key = currentMediaKey
                if (key.isBlank() || surroundAppliedKey == key) return
                for (group in tracks.groups) {
                    if (group.type != C.TRACK_TYPE_AUDIO) continue
                    for (i in 0 until group.length) {
                        val f = group.getTrackFormat(i)
                        if (f.channelCount >= 6) {
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, listOf(i)),
                                    ),
                            )
                            surroundAppliedKey = key
                            Log.i(
                                "PlayerManager",
                                "Audio envolvente auto: ${f.sampleMimeType} ${f.channelCount}ch",
                            )
                            return
                        }
                    }
                }
                // Sin pista 5.1+: marcar para no reintentar en cada onTracksChanged.
                surroundAppliedKey = key
            }
        })
    }

    // ---------------- Pistas de audio / subtítulos ----------------

    data class TrackOption(val groupIndex: Int, val trackIndex: Int, val label: String)

    fun audioTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_AUDIO)

    fun textTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_TEXT)

    private fun trackOptions(trackType: Int): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        val groups = player.currentTracks.groups
        for (gi in 0 until groups.size) {
            val group = groups[gi]
            if (group.type != trackType || group.length == 0) continue
            for (ti in 0 until group.length) {
                val f = group.getTrackFormat(ti)
                val label = f.label ?: f.language ?: f.sampleMimeType ?: "Pista ${ti + 1}"
                out += TrackOption(gi, ti, label)
            }
        }
        return out
    }

    fun selectAudio(groupIndex: Int, trackIndex: Int) {
        userAudioOverride = true
        val group = player.currentTracks.groups[groupIndex]
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
        )
    }

    fun clearAudioOverride() {
        userAudioOverride = false
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        )
    }

    fun selectText(groupIndex: Int, trackIndex: Int) {
        val group = player.currentTracks.groups[groupIndex]
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
        )
    }

    fun disableText() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }
}
