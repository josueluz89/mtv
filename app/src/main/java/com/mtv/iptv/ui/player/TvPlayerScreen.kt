package com.mtv.iptv.ui.player

import android.view.SurfaceView
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
 * Reproductor para TV (Google TV / Firestick) con motor libVLC (v1.3).
 *
 * Video: [SurfaceView] (ver [VlcPlayer.attachSurface]). Los controles son
 * Compose 100% navegables con D-pad: al mostrarse, el foco va al botón
 * play/pausa; izquierda/derecha se mueve entre botones; OK activa.
 * Cualquier tecla (menos Atrás) con los controles ocultos los vuelve a mostrar.
 *
 * En TV se continúa automáticamente donde quedó (sin diálogo, como antes).
 */
@Composable
fun TvPlayerScreen(
    url: String,
    title: String,
    mediaKey: String,
    imageUrl: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val manager: VlcPlayer = remember { container.vlcPlayer }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var controlsVisible by remember { mutableStateOf(true) }
    var hideToken by remember { mutableIntStateOf(0) }
    var indicator by remember { mutableStateOf<PlayerIndicator?>(null) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var aspectMode by remember { mutableStateOf(VlcAspectMode.FIT) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrubTo by remember { mutableStateOf<Long?>(null) }

    val playFocus = remember { FocusRequester() }

    /** Muestra los controles y reinicia el temporizador de ocultado. */
    fun pokeControls() {
        controlsVisible = true
        hideToken++
    }

    LaunchedEffect(hideToken) {
        if (!controlsVisible) return@LaunchedEffect
        delay(4000)
        // No ocultar mientras hay un diálogo abierto.
        if (!showSpeed && !showAudio && !showSubs && !showSleep && !showStreamInfo) {
            controlsVisible = false
        }
    }

    // Al mostrar los controles, llevar el foco al play/pausa (D-pad).
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(120)
            runCatching { playFocus.requestFocus() }
        }
    }

    LaunchedEffect(indicator) {
        if (indicator != null) {
            delay(1200)
            indicator = null
        }
    }

    // Sondeo del estado de reproducción.
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

    // Arranque: continuar automáticamente donde quedó (sin diálogo en TV).
    LaunchedEffect(url, mediaKey) {
        val defaultSpeed = container.userPrefs.defaultSpeed.first()
        speed = defaultSpeed
        val resumeEnabled = container.userPrefs.resumeEnabled.first()
        val saved = container.playbackRepository.get(mediaKey)
        val startAt = if (resumeEnabled && saved != null && saved.positionMs > 10_000 &&
            (saved.durationMs <= 0 || saved.positionMs < saved.durationMs - 15_000)
        ) {
            saved.positionMs
        } else {
            0L
        }
        manager.play(container.resolvePlaybackUrl(url), mediaKey, title, imageUrl, startAt)
        manager.setRate(defaultSpeed)
    }

    // Al terminar, mostrar los controles.
    DisposableEffect(Unit) {
        manager.setOnEndedListener { pokeControls() }
        onDispose { manager.setOnEndedListener(null) }
    }

    // Guardar posición al pausar la activity / salir.
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

    fun showIndicator(icon: ImageVector, text: String) {
        indicator = PlayerIndicator(icon, text)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                // Atrás siempre sale (no lo consumimos).
                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    return@onPreviewKeyEvent false
                }
                val wasHidden = !controlsVisible
                pokeControls()
                // Con controles ocultos, la primera tecla solo los muestra.
                wasHidden
            },
    ) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { manager.attachSurface(it) } },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            // Título arriba a la izquierda (como en la rama TV anterior).
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                    IconButton(
                        onClick = { togglePlay() },
                        modifier = Modifier.focusRequester(playFocus),
                    ) {
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
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSubs = true }) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = "Subtítulos", tint = Color.White)
                    }
                    IconButton(onClick = { showAudio = true }) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White)
                    }
                    IconButton(onClick = { showSpeed = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "Velocidad (${speed}x)", tint = Color.White)
                    }
                    IconButton(onClick = { cycleAspect() }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Ajuste de pantalla", tint = Color.White)
                    }
                    IconButton(onClick = { showSleep = true }) {
                        Icon(Icons.Default.Bedtime, contentDescription = "Temporizador", tint = Color.White)
                    }
                    IconButton(onClick = { showStreamInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Información del stream", tint = Color.White)
                    }
                }
            }
        }

        // Indicador de gesto (volumen / brillo / seek)
        Box(modifier = Modifier.align(Alignment.Center)) {
            PlayerIndicatorCard(indicator)
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
