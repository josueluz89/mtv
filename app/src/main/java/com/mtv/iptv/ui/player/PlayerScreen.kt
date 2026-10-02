package com.mtv.iptv.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.util.Rational
import android.util.TypedValue
import android.graphics.Color as AndroidColor
import androidx.media3.ui.CaptionStyleCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.xtream.XtreamEpisode
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.VodDurationProbe
import com.mtv.iptv.player.ZapChannel
import com.mtv.iptv.player.attachExternalSubtitle
import com.mtv.iptv.player.downloads.PLAYER_USER_AGENT
import com.mtv.iptv.ui.mobile.safeClickable
import com.mtv.iptv.util.formatMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class Indicator(val icon: ImageVector, val text: String)

/** Tamaño de subtítulos: etiqueta -> dip. */
private fun subtitleDip(size: String): Float = when (size) {
    "S" -> 14f
    "L" -> 24f
    else -> 18f
}

/**
 * Reproductor para celular: PlayerView (Media3) + overlay Compose con gestos:
 * - Swipe vertical izquierda: brillo | derecha: volumen
 * - Swipe horizontal: seek con indicador
 * - Doble tap laterales: ±10s
 * - Botones: subtítulos, audio, velocidad, ajuste de pantalla, PiP, bloqueo
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    mediaKey: String,
    imageUrl: String,
    onBack: () -> Unit,
    zapChannels: List<ZapChannel> = emptyList(),
    /** TMDB ID para buscar subtítulos por ID (null = sin búsqueda, p. ej. en vivo). */
    subTmdbId: Int? = null,
    subSeason: Int? = null,
    subEpisode: Int? = null,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val container = LocalAppContainer.current
    val manager = remember { container.playerManager }
    // El player puede reconstruirse si cambian los ajustes de decodificación
    // (ver PlayerManager.playerEpoch): re-leerlo para no quedarnos con una
    // instancia obsoleta.
    val playerEpoch by manager.playerEpoch.collectAsState()
    val player = remember(playerEpoch) { manager.player }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var resumeFrom by remember { mutableStateOf<PlaybackEntity?>(null) }
    var resumeChecked by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var indicator by remember { mutableStateOf<Indicator?>(null) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    /** Diálogo de búsqueda de subtítulos por TMDB ID (OpenSubtitles). */
    var showSubSearch by remember { mutableStateOf(false) }
    val openSubtitlesKey by container.userPrefs.openSubtitlesKey.collectAsState(initial = "")
    var showSleep by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Ajustes de usuario
    val pipEnabled by container.userPrefs.pipEnabled.collectAsState(initial = true)
    val subtitleSize by container.userPrefs.subtitleSize.collectAsState(initial = "M")
    val subtitleBackground by container.userPrefs.subtitleBackground.collectAsState(initial = "semi")
    val subtitleColor by container.userPrefs.subtitleColor.collectAsState(initial = "blanco")
    // Clave del medio actual (cambia si el autoplay salta al siguiente episodio
    // o si el zapping cambia de canal en vivo).
    var currentKey by remember { mutableStateOf(mediaKey) }
    // Título visible (el zapping en vivo lo actualiza sin recrear la pantalla).
    var currentTitle by remember { mutableStateOf(title) }

    /** Zapping disponible solo en TV en vivo con lista de canales. */
    val isLiveZap = mediaKey.startsWith("live:") && zapChannels.isNotEmpty()

    /** Película/serie (no en vivo): aquí sí aplica adelantar/retroceder. */
    val isVod = !mediaKey.startsWith("live:")
    /** Duración que reporta ExoPlayer (<= 0 cuando el servidor no la entrega). */
    var exoDurationMs by remember { mutableStateOf(0L) }
    /** Duración pedida directamente al servidor cuando ExoPlayer no la recibe. */
    var probedDurationMs by remember { mutableStateOf<Long?>(null) }
    /** Posición actual (para la barra de progreso manual). */
    var positionMs by remember { mutableStateOf(0L) }

    /** Cambia al canal en vivo indicado sin salir del reproductor. */
    fun zapTo(channel: ZapChannel) {
        val repo = container.xtreamRepository
        manager.play(
            repo.liveUrl(channel.streamId),
            "live:${channel.streamId}",
            channel.name,
            channel.icon,
            0,
        )
        manager.player.setPlaybackSpeed(1f)
        speed = 1f
        currentKey = "live:${channel.streamId}"
        currentTitle = channel.name
        resumeFrom = null
        indicator = Indicator(Icons.Default.List, "Canal: ${channel.name}")
    }

    /** Zapping por pasos: -1 anterior, +1 siguiente (con vuelta al inicio/fin). */
    fun zapStep(delta: Int) {
        if (zapChannels.isEmpty()) return
        val currentId = currentKey.removePrefix("live:").toIntOrNull()
        val idx = zapChannels.indexOfFirst { it.streamId == currentId }
        val next = if (idx < 0) {
            zapChannels.first()
        } else {
            zapChannels[(idx + delta).mod(zapChannels.size)]
        }
        zapTo(next)
    }

    /** Aplica el tamaño de subtítulos elegido al SubtitleView del PlayerView. */
    /**
     * Aplica el estilo de subtítulos elegido en Ajustes → Subtítulos:
     * tamaño, fondo del recuadro y color del texto. Se ignoran los estilos
     * embebidos del stream para que el ajuste del usuario siempre gane.
     */
    fun applySubtitleStyle(size: String, background: String, color: String) {
        try {
            val subtitleView = playerViewRef?.subtitleView ?: return
            subtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_DIP,
                subtitleDip(size),
            )
            subtitleView.setApplyEmbeddedStyles(false)
            val fg = when (color) {
                "amarillo" -> AndroidColor.YELLOW
                "cian" -> AndroidColor.CYAN
                "verde" -> AndroidColor.GREEN
                else -> AndroidColor.WHITE
            }
            val style = when (background) {
                "solido" -> CaptionStyleCompat(
                    fg, AndroidColor.BLACK, AndroidColor.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.BLACK, null,
                )
                "ninguno" -> CaptionStyleCompat(
                    fg, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, AndroidColor.BLACK, null,
                )
                else -> CaptionStyleCompat(
                    fg, 0x99000000.toInt(), AndroidColor.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.BLACK, null,
                )
            }
            subtitleView.setStyle(style)
        } catch (_: Exception) {
        }
    }

    // Aplica el estilo de subtítulos cuando cambia un ajuste o se crea el PlayerView.
    LaunchedEffect(subtitleSize, subtitleBackground, subtitleColor, playerViewRef) {
        applySubtitleStyle(subtitleSize, subtitleBackground, subtitleColor)
    }

    /** Arranca la reproducción aplicando la velocidad por defecto del ajuste. */
    fun startPlayback(positionMs: Long) {
        manager.play(url, mediaKey, title, imageUrl, positionMs)
        // Leer dinámico: play() puede haber reconstruido el player.
        manager.player.setPlaybackSpeed(speed)
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
                repo.episodeUrl(ep.id, ep.containerExtension),
                nextKey,
                nextTitle,
                ep.info.movieImage.ifBlank { imageUrl },
                0,
            )
            player.setPlaybackSpeed(defaultSpeed)
            speed = defaultSpeed
            currentKey = nextKey
        } catch (_: Exception) {
        }
    }

    fun showIndicator(icon: ImageVector, text: String) {
        indicator = Indicator(icon, text)
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
                player.pause()
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

    // Sondeo de posición/duración + probe de duración al servidor.
    //
    // Parte del contenido VOD no le entrega la duración a ExoPlayer al inicio
    // (el átomo moov del MP4 va al final del archivo y el servidor no anuncia
    // soporte de rangos): el reproductor lo trata como "en vivo" y bloquea
    // adelantar/retroceder. Si tras ~3 s ExoPlayer sigue sin duración, se le
    // pide directamente al servidor (VodDurationProbe) y con eso se muestra
    // una barra de progreso manual cuyos saltos se intentan siempre, igual
    // que hacen otros reproductores.
    LaunchedEffect(url, mediaKey, player) {
        probedDurationMs = null
        var probeDone = false
        var ticksSinDuracion = 0
        while (true) {
            delay(500)
            try {
                positionMs = player.currentPosition
                exoDurationMs = player.duration
                if (isVod && !probeDone && exoDurationMs <= 0 &&
                    player.playbackState != Player.STATE_IDLE
                ) {
                    ticksSinDuracion++
                    if (ticksSinDuracion >= 6) {
                        probeDone = true
                        val d = VodDurationProbe.probe(url, PLAYER_USER_AGENT)
                        if (d != null && d > 0) probedDurationMs = d
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // Siguiente episodio automático al terminar (si el ajuste está activo).
    DisposableEffect(mediaKey, player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch { tryPlayNextEpisode() }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
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
            playerViewRef?.player = null
        }
    }

    fun seekBy(ms: Long) {
        val newPos = (player.currentPosition + ms).coerceAtLeast(0)
        player.seekTo(newPos)
        val label = (if (ms >= 0) "+" else "-") + formatMs(abs(ms))
        showIndicator(if (ms >= 0) Icons.Default.FastForward else Icons.Default.FastRewind, label)
    }

    fun enterPip() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }

    fun cycleResize() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerViewRef?.resizeMode = resizeMode
        val label = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Llenar"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
            else -> "Ajustar"
        }
        showIndicator(Icons.Default.AspectRatio, label)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = manager.player
                    useController = true
                    setShowPreviousButton(false)
                    setShowNextButton(false)
                    setControllerShowTimeoutMs(3000)
                    playerViewRef = this
                }
            },
            update = {
                it.useController = !locked
                if (it.player !== player) it.player = player
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewSize = it.size }
                .pointerInput(locked) {
                    if (!locked) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                if (offset.x < viewSize.width / 2) seekBy(-10_000) else seekBy(10_000)
                            },
                        )
                    }
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
                                player.seekTo((player.currentPosition + s * 1000L).coerceAtLeast(0))
                            }
                        },
                    )
                },
        )

        // Barra superior con acciones
        if (!locked) {
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
                    currentTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Zapping en vivo: anterior / lista / siguiente sin salir.
                if (isLiveZap) {
                    IconButton(onClick = { zapStep(-1) }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Canal anterior", tint = Color.White)
                    }
                    IconButton(onClick = { showChannels = true }) {
                        Icon(Icons.Default.List, contentDescription = "Lista de canales", tint = Color.White)
                    }
                    IconButton(onClick = { zapStep(1) }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Canal siguiente", tint = Color.White)
                    }
                }
                IconButton(onClick = { showSubs = true }) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = "Subtítulos", tint = Color.White)
                }
                IconButton(onClick = { showAudio = true }) {
                    Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White)
                }
                IconButton(onClick = { showSpeed = true }) {
                    Icon(Icons.Default.Speed, contentDescription = "Velocidad", tint = Color.White)
                }
                IconButton(onClick = { cycleResize() }) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Ajuste de pantalla", tint = Color.White)
                }
                IconButton(onClick = { showStreamInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Información del stream", tint = Color.White)
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Default.Bedtime, contentDescription = "Temporizador", tint = Color.White)
                }
                if (pipEnabled) {
                    IconButton(onClick = { enterPip() }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Ventana flotante", tint = Color.White)
                    }
                }
                IconButton(onClick = { locked = true }) {
                    Icon(Icons.Default.Lock, contentDescription = "Bloquear controles", tint = Color.White)
                }
            }
        } else {
            IconButton(
                onClick = { locked = false },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Desbloquear", tint = Color.White)
            }
        }

        // Barra de progreso manual: solo cuando ExoPlayer no recibió la
        // duración del servidor pero el probe sí la obtuvo. Los saltos se
        // intentan siempre contra el servidor.
        if (isVod && !locked && probedDurationMs != null && exoDurationMs <= 0) {
            val total = probedDurationMs ?: 0L
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatMs(positionMs), color = Color.White, fontSize = 12.sp)
                    Text(formatMs(total), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Slider(
                    value = positionMs.coerceAtMost(total).toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..total.toFloat(),
                )
            }
        }

        // Indicador de gesto (volumen / brillo / seek)
        indicator?.let { ind ->
            Card(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ind.icon, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(ind.text, color = Color.White)
                }
            }
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
                player.setPlaybackSpeed(it)
            },
            onDismiss = { showSpeed = false },
        )
    }
    if (showAudio) {
        AudioTrackDialog(manager = manager, onDismiss = { showAudio = false })
    }
    if (showSubs) {
        SubtitleTrackDialog(
            manager = manager,
            subtitleSize = subtitleSize,
            onSizeSelect = { size ->
                scope.launch {
                    try {
                        container.userPrefs.setSubtitleSize(size)
                    } catch (_: Exception) {
                    }
                }
            },
            onSearchClick = if (subTmdbId != null) {
                { showSubs = false; showSubSearch = true }
            } else {
                null
            },
            onDismiss = { showSubs = false },
        )
    }
    // Búsqueda de subtítulos por TMDB ID (OpenSubtitles): descarga y activa.
    if (showSubSearch && subTmdbId != null) {
        SubtitleSearchDialog(
            apiKey = openSubtitlesKey,
            tmdbId = subTmdbId,
            season = subSeason,
            episode = subEpisode,
            onSubtitleReady = { file ->
                manager.attachExternalSubtitle(context, file, "Español")
                showSubSearch = false
            },
            onDismiss = { showSubSearch = false },
        )
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

    // Lista rápida de canales (zapping en vivo).
    if (showChannels && isLiveZap) {
        val currentId = currentKey.removePrefix("live:").toIntOrNull()
        AlertDialog(
            onDismissRequest = { showChannels = false },
            title = { Text("Canales") },
            text = {
                LazyColumn {
                    items(zapChannels, key = { it.streamId }) { ch ->
                        val selected = ch.streamId == currentId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeClickable(
                                    onClick = {
                                        zapTo(ch)
                                        showChannels = false
                                    },
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (ch.num > 0) {
                                Text(
                                    ch.num.toString(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(44.dp),
                                )
                            }
                            AsyncImage(
                                model = ch.icon.ifBlank { null },
                                contentDescription = ch.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.2f)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                ch.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChannels = false }) { Text("Cerrar") } },
        )
    }
}
