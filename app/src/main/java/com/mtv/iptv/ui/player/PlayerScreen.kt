package com.mtv.iptv.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.util.Rational
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.xtream.XtreamEpisode
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.vlc.VlcAspectMode
import com.mtv.iptv.player.vlc.VlcPlayer
import com.mtv.iptv.util.formatMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Reproductor para celular con motor libVLC (v1.3).
 *
 * Video: [SurfaceView] donde libVLC renderiza vía `IVLCVout.attachViews`
 * (ver [VlcPlayer.attachSurface]). Los controles son 100% Compose:
 * - Barra superior: título, subtítulos, audio, velocidad, ajuste de pantalla,
 *   info del stream, temporizador, PiP, bloqueo.
 * - Barra inferior: play/pausa, seek ±10s, barra de progreso con tiempos.
 * - Gestos sobre el video: swipe vertical izquierda = brillo, derecha = volumen,
 *   swipe horizontal = seek, doble tap laterales = ±10s, tap = mostrar/ocultar.
 * - "Continuar desde HH:MM", sleep timer, siguiente episodio automático.
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    mediaKey: String,
    imageUrl: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val container = LocalAppContainer.current
    val manager: VlcPlayer = remember { container.vlcPlayer }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var resumeFrom by remember { mutableStateOf<PlaybackEntity?>(null) }
    var resumeChecked by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var indicator by remember { mutableStateOf<PlayerIndicator?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var hideToken by remember { mutableIntStateOf(0) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var aspectMode by remember { mutableStateOf(VlcAspectMode.FIT) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Estado de reproducción (sondeo cada 500 ms: libVLC no tiene Flow).
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrubTo by remember { mutableStateOf<Long?>(null) }

    // Ajustes de usuario
    val pipEnabled by container.userPrefs.pipEnabled.collectAsState(initial = true)
    // Clave del medio actual (cambia si el autoplay salta al siguiente episodio).
    var currentKey by remember { mutableStateOf(mediaKey) }

    /** Muestra los controles y reinicia el temporizador de ocultado. */
    fun pokeControls() {
        controlsVisible = true
        hideToken++
    }

    /** Oculta los controles 3.5 s después de la última interacción. */
    LaunchedEffect(hideToken) {
        if (!controlsVisible) return@LaunchedEffect
        delay(3500)
        controlsVisible = false
    }

    /** Arranca la reproducción aplicando la velocidad por defecto del ajuste. */
    fun startPlayback(startAtMs: Long) {
        manager.play(container.resolvePlaybackUrl(url), mediaKey, title, imageUrl, startAtMs)
        manager.setRate(speed)
        currentKey = mediaKey
    }

    /**
     * Siguiente episodio automático: defensivo. Si el mediaKey no es de un
     * episodio ("ep:<id>"), si no se deduce la serie del título o si algo
     * falla, no hace nada (no rompe la reproducción actual).
     */
    suspend fun tryPlayNextEpisode() {
        try {
            if (!container.userPrefs.autoplayNext.first()) return
            val key = currentKey
            if (!key.startsWith("ep:")) return
            val episodeId = key.removePrefix("ep:")
            if (episodeId.isBlank()) return
            // Formato de SeriesDetailScreen: "$serie — $episodio".
            val seriesName = title.substringBefore(" — ").trim()
            if (seriesName.isBlank() || seriesName == title) return
            val repo = container.xtreamRepository
            val series = repo.findSeriesByTitle(TitleCleaner.clean(seriesName).title) ?: return
            if (series.seriesId == 0) return
            val info = repo.getSeriesInfo(series.seriesId)
            val seasons = info.episodes.keys.mapNotNull { it.toIntOrNull() }.sorted()
            var found = false
            var next: XtreamEpisode? = null
            for (s in seasons) {
                val eps = info.episodes[s.toString()].orEmpty().sortedBy { it.episodeNum }
                if (found) {
                    next = eps.firstOrNull()
                    break
                }
                val idx = eps.indexOfFirst { it.id == episodeId }
                if (idx >= 0) {
                    found = true
                    if (idx + 1 < eps.size) {
                        next = eps[idx + 1]
                        break
                    }
                    // Último de la temporada: sigue el primero de la próxima.
                }
            }
            val ep = next ?: return
            val nextTitle = "$seriesName — ${ep.title.ifBlank { "Episodio ${ep.episodeNum}" }}"
            val nextKey = "ep:${ep.id}"
            val defaultSpeed = container.userPrefs.defaultSpeed.first()
            manager.play(
                container.resolvePlaybackUrl(repo.episodeUrl(ep.id, ep.containerExtension)),
                nextKey,
                nextTitle,
                ep.info.movieImage.ifBlank { imageUrl },
                0,
            )
            manager.setRate(defaultSpeed)
            speed = defaultSpeed
            currentKey = nextKey
            pokeControls()
        } catch (_: Exception) {
        }
    }

    fun showIndicator(icon: ImageVector, text: String) {
        indicator = PlayerIndicator(icon, text)
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
    }

    fun startSleep(minutes: Int) {
        cancelSleep()
        sleepMinutes = minutes
        if (minutes > 0) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                manager.pause()
                showIndicator(Icons.Default.Bedtime, "Temporizador: pausado")
            }
            showIndicator(Icons.Default.Bedtime, "Temporizador: $minutes min")
        } else {
            showIndicator(Icons.Default.Bedtime, "Temporizador apagado")
        }
    }

    LaunchedEffect(indicator) {
        if (indicator != null) {
            delay(900)
            indicator = null
        }
    }

    // Sondeo del estado de reproducción (posición/duración/play-pausa).
    LaunchedEffect(Unit) {
        while (true) {
            try {
                positionMs = manager.positionMs
                durationMs = manager.durationMs
                isPlaying = manager.isPlaying
            } catch (_: Exception) {
            }
            delay(500)
        }
    }

    // "Continuar desde HH:MM" si hay posición guardada (y el ajuste lo permite).
    // Aplica la velocidad por defecto al iniciar la reproducción.
    LaunchedEffect(url, mediaKey) {
        val defaultSpeed = container.userPrefs.defaultSpeed.first()
        speed = defaultSpeed
        val resumeEnabled = container.userPrefs.resumeEnabled.first()
        val saved = container.playbackRepository.get(mediaKey)
        if (resumeEnabled && saved != null && saved.positionMs > 10_000 &&
            (saved.durationMs <= 0 || saved.positionMs < saved.durationMs - 15_000)
        ) {
            resumeFrom = saved
        } else {
            startPlayback(0)
        }
        resumeChecked = true
    }

    // Siguiente episodio automático al terminar (si el ajuste está activo).
    DisposableEffect(currentKey) {
        manager.setOnEndedListener { scope.launch { tryPlayNextEpisode() } }
        onDispose { manager.setOnEndedListener(null) }
    }

    // Guardar posición al pausar la activity
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                scope.launch { manager.savePosition() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { manager.savePosition() }
            manager.stop()
            manager.detachSurface()
        }
    }

    fun seekBy(ms: Long) {
        val newPos = (manager.positionMs + ms).coerceAtLeast(0)
        manager.seekTo(newPos)
        val label = (if (ms >= 0) "+" else "-") + formatMs(abs(ms))
        showIndicator(if (ms >= 0) Icons.Default.FastForward else Icons.Default.FastRewind, label)
        pokeControls()
    }

    fun togglePlay() {
        if (manager.isPlaying) {
            manager.pause()
            showIndicator(Icons.Default.Pause, "Pausa")
        } else {
            manager.resume()
            showIndicator(Icons.Default.PlayArrow, "Reproduciendo")
        }
        pokeControls()
    }

    fun enterPip() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }

    fun cycleAspect() {
        aspectMode = when (aspectMode) {
            VlcAspectMode.FIT -> VlcAspectMode.FILL
            VlcAspectMode.FILL -> VlcAspectMode.ZOOM
            VlcAspectMode.ZOOM -> VlcAspectMode.FIT
        }
        manager.setAspectMode(aspectMode)
        val label = when (aspectMode) {
            VlcAspectMode.FIT -> "Ajustar"
            VlcAspectMode.FILL -> "Llenar"
            VlcAspectMode.ZOOM -> "Zoom"
        }
        showIndicator(Icons.Default.AspectRatio, label)
        pokeControls()
    }

    val showTopBar = controlsVisible && !locked

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { manager.attachSurface(it) }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewSize = it.size }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            hideToken++
                        },
                        onDoubleTap = { offset ->
                            if (offset.x < viewSize.width / 2) seekBy(-10_000) else seekBy(10_000)
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var baseVolume = 0
                    var acc = 0f
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    detectVerticalDragGestures(
                        onDragStart = {
                            baseVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            acc = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val leftHalf = change.position.x < viewSize.width / 2
                            if (leftHalf) {
                                // Brillo
                                val window = activity.window
                                val attrs = window.attributes
                                val current = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                val next = (current - dragAmount / 600f).coerceIn(0.05f, 1f)
                                attrs.screenBrightness = next
                                window.attributes = attrs
                                showIndicator(Icons.Default.Brightness6, "Brillo ${(next * 100).toInt()}%")
                            } else {
                                // Volumen
                                acc += dragAmount
                                if (abs(acc) > 60) {
                                    val steps = (acc / 60).toInt()
                                    val next = (baseVolume - steps).coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    baseVolume = next
                                    acc = 0f
                                    showIndicator(Icons.Default.VolumeUp, "Volumen $next/$maxVolume")
                                }
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            acc += dragAmount
                            val s = (acc / 40).toInt()
                            if (s != 0) {
                                showIndicator(
                                    if (s > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                    (if (s > 0) "+" else "") + "$s s",
                                )
                            }
                        },
                        onDragEnd = {
                            val s = (acc / 40).toInt()
                            if (s != 0) {
                                manager.seekTo((manager.positionMs + s * 1000L).coerceAtLeast(0))
                                pokeControls()
                            }
                        },
                    )
                },
        )

        // Barra superior con acciones
        if (showTopBar) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showSubs = true }) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = "Subtítulos", tint = Color.White)
                }
                IconButton(onClick = { showAudio = true }) {
                    Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White)
                }
                IconButton(onClick = { showSpeed = true }) {
                    Icon(Icons.Default.Speed, contentDescription = "Velocidad", tint = Color.White)
                }
                IconButton(onClick = { cycleAspect() }) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Ajuste de pantalla", tint = Color.White)
                }
                IconButton(onClick = { showStreamInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Información del stream", tint = Color.White)
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Default.Bedtime, contentDescription = "Temporizador", tint = Color.White)
                }
                if (pipEnabled && manager.supportsPip) {
                    IconButton(onClick = { enterPip() }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Ventana flotante", tint = Color.White)
                    }
                }
                IconButton(onClick = { locked = true }) {
                    Icon(Icons.Default.Lock, contentDescription = "Bloquear controles", tint = Color.White)
                }
            }
        }

        // Barra inferior: play/pausa, ±10s, progreso
        if (showTopBar) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                val max = durationMs.coerceAtLeast(1L)
                val shown = scrubTo ?: positionMs
                Slider(
                    value = shown.toFloat().coerceIn(0f, max.toFloat()),
                    onValueChange = {
                        scrubTo = it.toLong()
                        pokeControls()
                    },
                    onValueChangeFinished = {
                        scrubTo?.let { manager.seekTo(it) }
                        scrubTo = null
                        pokeControls()
                    },
                    valueRange = 0f..max.toFloat(),
                    enabled = durationMs > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { togglePlay() }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { seekBy(-10_000) }) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Retroceder 10 segundos", tint = Color.White)
                    }
                    IconButton(onClick = { seekBy(10_000) }) {
                        Icon(Icons.Default.FastForward, contentDescription = "Adelantar 10 segundos", tint = Color.White)
                    }
                    Text(
                        text = if (durationMs > 0) {
                            "${formatMs(shown)} / ${formatMs(durationMs)}"
                        } else {
                            "${formatMs(shown)} · EN VIVO"
                        },
                        color = Color.White,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x",
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }

        // Botón de desbloqueo cuando está bloqueado
        if (locked) {
            IconButton(
                onClick = { locked = false },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Desbloquear", tint = Color.White)
            }
        }

        // Indicador de gesto (volumen / brillo / seek)
        Box(modifier = Modifier.align(Alignment.Center)) {
            PlayerIndicatorCard(indicator)
        }
    }

    // Diálogo "Continuar desde HH:MM"
    if (resumeChecked) {
        resumeFrom?.let { saved ->
            AlertDialog(
                onDismissRequest = {
                    resumeFrom = null
                    startPlayback(0)
                },
                title = { Text("Continuar viendo") },
                text = { Text("¿Continuar desde ${formatMs(saved.positionMs)}?") },
                confirmButton = {
                    TextButton(onClick = {
                        startPlayback(saved.positionMs)
                        resumeFrom = null
                    }) { Text("Continuar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        startPlayback(0)
                        resumeFrom = null
                    }) { Text("Desde el inicio") }
                },
            )
        }
    }

    if (showSpeed) {
        SpeedDialog(
            current = speed,
            onSelect = {
                speed = it
                manager.setRate(it)
                pokeControls()
            },
            onDismiss = { showSpeed = false },
        )
    }
    if (showAudio) {
        AudioTrackDialog(manager = manager, onDismiss = { showAudio = false })
    }
    if (showSubs) {
        SubtitleTrackDialog(manager = manager, onDismiss = { showSubs = false })
    }
    if (showSleep) {
        SleepTimerDialog(
            selectedMinutes = sleepMinutes,
            onSelect = { startSleep(it) },
            onDismiss = { showSleep = false },
        )
    }
    if (showStreamInfo) {
        StreamInfoDialog(manager = manager, onDismiss = { showStreamInfo = false })
    }
}
