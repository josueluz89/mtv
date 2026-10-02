package com.mtv.iptv.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.remote.subs.OpenSubtitlesClient
import com.mtv.iptv.data.remote.subs.SubtitleResult
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.player.PlayerManager
import com.mtv.iptv.player.ZapChannel
import com.mtv.iptv.player.attachExternalSubtitle
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvRed
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import com.mtv.iptv.ui.components.MtvAsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.KeyEvent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * Controlador del reproductor en TV, estilo TiviMate: el PlayerView nativo
 * queda SIN su controlador (useController = false) y todo vive aquí —
 * título con badges de calidad (resolución, fps, audio), barra de progreso
 * con tiempo actual/total, play/pausa, adelantar/retroceder, Audio,
 * Subtítulos y una segunda fila con Canales, PiP, Aspecto, Sleep,
 * Favorito, Externo y Opciones.
 *
 * Todo es operable con D-pad: OK muestra/oculta los controles; con los
 * controles visibles el foco arranca en play/pausa, izquierda/derecha
 * navega entre botones y sobre la barra de progreso salta ∓10 s.
 *
 * El ExoPlayer puede reconstruirse en caliente (PlayerManager.playerEpoch)
 * cuando cambian los ajustes de decodificación: el sondeo lee
 * `manager.player` dinámicamente en cada iteración y la UI recompone con
 * la instancia vigente.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerOverlay(
    title: String,
    isLive: Boolean,
    mediaKey: String,
    manager: PlayerManager,
    zapChannels: List<ZapChannel>,
    subTmdbId: Int?,
    subSeason: Int?,
    subEpisode: Int?,
    onAspectChange: (String) -> Unit,
    onPipClick: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    // Epoch del player: si el PlayerManager lo reconstruye, la UI recompone
    // (el sondeo y los diálogos leen siempre la instancia vigente).
    val epoch by manager.playerEpoch.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showSubSearch by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    // Título/clave visibles: el zapping en vivo los actualiza sin recrear nada.
    var currentKey by remember(mediaKey) { mutableStateOf(mediaKey) }
    var currentTitle by remember(title) { mutableStateOf(title) }

    val subtitleSize by container.userPrefs.subtitleSize.collectAsState(initial = "M")
    val openSubtitlesKey by container.userPrefs.openSubtitlesKey.collectAsState(initial = "")
    val pipEnabled by container.userPrefs.pipEnabled.collectAsState(initial = true)
    val aspectPref by container.userPrefs.aspectRatio.collectAsState(initial = "fit")

    // Posición/duración/estado/formatos: se sondea 2 veces por segundo
    // leyendo SIEMPRE la instancia vigente del player.
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var videoW by remember { mutableStateOf(0) }
    var videoH by remember { mutableStateOf(0) }
    var videoFps by remember { mutableStateOf(0f) }
    var audioBadge by remember { mutableStateOf("") }
    LaunchedEffect(epoch) {
        while (true) {
            val p = manager.player
            positionMs = p.currentPosition.coerceAtLeast(0L)
            durationMs = p.duration.let { if (it > 0) it else 0L }
            playing = p.isPlaying
            val vf = p.videoFormat
            videoW = vf?.width ?: 0
            videoH = vf?.height ?: 0
            videoFps = vf?.frameRate ?: 0f
            audioBadge = selectedAudioBadge(p)
            delay(500)
        }
    }

    val anyDialog = showAudio || showSubs || showSubSearch ||
        showChannels || showSleep || showOptions
    // Auto-ocultar: 4 s sin interacción (con un diálogo abierto no se oculta).
    LaunchedEffect(controlsVisible, interactionTick, anyDialog) {
        if (controlsVisible && !anyDialog) {
            delay(4000)
            controlsVisible = false
        }
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(controlsVisible, anyDialog) {
        if (controlsVisible && !anyDialog) {
            // Pequeña espera para que los botones ya estén compuestos.
            delay(120)
            try {
                playFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        val p = manager.player
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        val dur = p.duration
        p.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
        interactionTick++
    }

    fun toggleControls() {
        controlsVisible = !controlsVisible
        interactionTick++
    }

    // ---------------- Zapping en vivo ----------------

    fun zapTo(channel: ZapChannel) {
        manager.play(
            container.xtreamRepository.liveUrl(channel.streamId),
            "live:${channel.streamId}",
            channel.name,
            channel.icon,
            0,
        )
        currentKey = "live:${channel.streamId}"
        currentTitle = channel.name
        scope.launch {
            try {
                container.userPrefs.setLastLiveChannel("${channel.streamId}|${channel.name}")
            } catch (_: Exception) {
            }
        }
        interactionTick++
    }

    // ---------------- Favorito (solo en vivo) ----------------

    val streamId = currentKey.removePrefix("live:")
    var isFav by remember(currentKey) { mutableStateOf(false) }
    LaunchedEffect(currentKey) {
        isFav = try {
            val sid = container.xtreamRepository.session?.server?.id ?: 0L
            sid != 0L && container.favoritesRepository.isFavorite(sid, "live", streamId)
        } catch (_: Exception) {
            false
        }
    }
    fun toggleFavorite() {
        scope.launch {
            try {
                val sid = container.xtreamRepository.session?.server?.id ?: return@launch
                isFav = container.favoritesRepository.toggle(
                    FavoriteEntity(
                        serverId = sid,
                        kind = "live",
                        refId = streamId,
                        name = currentTitle,
                        imageUrl = manager.currentImageUrl,
                    ),
                )
            } catch (_: Exception) {
            }
        }
        interactionTick++
    }

    // ---------------- Sleep timer ----------------

    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var sleepMinutes by remember { mutableStateOf(0) }
    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        sleepMinutes = minutes
        if (minutes > 0) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                onClose()
            }
        }
        interactionTick++
    }

    // ---------------- Aspecto ----------------

    fun cycleAspect() {
        val next = when (aspectPref) {
            "fit" -> "fill"
            "fill" -> "zoom"
            else -> "fit"
        }
        onAspectChange(next)
        interactionTick++
    }
    val aspectLabel = when (aspectPref) {
        "fill" -> "Llenar"
        "zoom" -> "Zoom"
        else -> "Ajustar"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        // Solo llega aquí si ningún botón/diálogo lo consumió.
                        toggleControls()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Barra de info superior: título + badges de calidad.
        if (controlsVisible && currentTitle.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.75f)
                    .padding(28.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    currentTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isLive) {
                    Spacer(Modifier.width(12.dp))
                    TvQualityBadge("EN VIVO", Color(0xFFE02020))
                }
                if (videoW > 0 && videoH > 0) {
                    Spacer(Modifier.width(12.dp))
                    TvQualityBadge("${videoW}x${videoH}")
                }
                if (videoFps > 0f) {
                    Spacer(Modifier.width(8.dp))
                    TvQualityBadge("%.0f FPS".format(videoFps))
                }
                if (audioBadge.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    TvQualityBadge(audioBadge)
                }
            }
        }
        if (controlsVisible) {
            // Navegador compacto de controles: línea de tiempo (solo VOD) →
            // fila de transporte → fila de opciones. Paddings mínimos para
            // que las tres filas quepan en pantalla sin cortarse.
            val timelineFr = remember { FocusRequester() }
            val transportFr = remember { FocusRequester() }
            val optionsFr = remember { FocusRequester() }

            // Anclas de foco para el primer botón de cada fila: encadenan el
            // D-pad (↓ baja de fila, ↑ sube) sin depender de la geometría.
            fun Modifier.transportAnchor(up: FocusRequester?): Modifier =
                focusRequester(transportFr).focusProperties {
                    if (up != null) this.up = up
                    down = optionsFr
                }

            fun Modifier.optionsAnchor(): Modifier =
                focusRequester(optionsFr).focusProperties { up = transportFr }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    )
                    .padding(horizontal = 40.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                // 1. Línea de tiempo (solo VOD).
                if (!isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatPlayerTime(positionMs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(16.dp))
                        TvPlayerSeekBar(
                            progress = if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else 0f,
                            onSeek = { seekBy(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(timelineFr)
                                .focusProperties { down = transportFr },
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            formatPlayerTime(durationMs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // 2. Fila de transporte: ∓10 s, play/pausa (foco inicial),
                //    Audio, Subtítulos.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isLive) {
                        TvPlayerButton(
                            label = "−10 s",
                            onClick = { seekBy(-10_000L) },
                            modifier = Modifier.transportAnchor(up = timelineFr),
                        )
                    }
                    Button(
                        onClick = {
                            val p = manager.player
                            if (playing) p.pause() else p.play()
                            interactionTick++
                        },
                        modifier = Modifier
                            .focusRequester(playFocus)
                            .then(
                                if (isLive) {
                                    // En vivo el play/pausa es el primero de la fila.
                                    Modifier.transportAnchor(up = null)
                                } else {
                                    Modifier.focusProperties { down = optionsFr }
                                },
                            ),
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                        )
                    }
                    if (!isLive) {
                        TvPlayerButton(label = "+10 s", onClick = { seekBy(10_000L) })
                    }
                    TvPlayerButton(label = "Audio", onClick = { showAudio = true })
                    TvPlayerButton(label = "Subtítulos", onClick = { showSubs = true })
                }
                Spacer(Modifier.height(8.dp))
                // 3. Fila de opciones.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLive) {
                        TvPlayerButton(
                            label = "Canales",
                            onClick = { showChannels = true },
                            modifier = Modifier.optionsAnchor(),
                        )
                    }
                    if (pipEnabled) {
                        TvPlayerButton(
                            label = "PiP",
                            onClick = { onPipClick() },
                            modifier = if (!isLive) Modifier.optionsAnchor() else Modifier,
                        )
                    }
                    TvPlayerButton(
                        label = "Aspecto: $aspectLabel",
                        onClick = { cycleAspect() },
                        modifier = if (!isLive && !pipEnabled) Modifier.optionsAnchor() else Modifier,
                    )
                    TvPlayerButton(
                        label = if (sleepMinutes > 0) "Sleep ${sleepMinutes}m" else "Sleep",
                        onClick = { showSleep = true },
                    )
                    if (isLive) {
                        TvPlayerButton(
                            label = if (isFav) "✓ ★ Favorito" else "★ Favorito",
                            onClick = { toggleFavorite() },
                        )
                    }
                    TvPlayerButton(
                        label = "Externo",
                        onClick = {
                            val activity = context as? ComponentActivity ?: return@TvPlayerButton
                            ExternalPlayer.playExternal(
                                activity,
                                manager.currentUrl,
                                currentTitle,
                                currentKey,
                                container,
                            )
                        },
                    )
                    TvPlayerButton(label = "Opciones", onClick = { showOptions = true })
                }
            }
        }
    }

    if (showAudio) {
        TvAudioTrackDialog(manager = manager, epoch = epoch, onDismiss = { showAudio = false })
    }
    if (showSubs) {
        TvSubtitleTrackDialog(
            manager = manager,
            epoch = epoch,
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
    if (showSubSearch && subTmdbId != null) {
        TvSubtitleSearchDialog(
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
    if (showChannels) {
        TvChannelListDialog(
            channels = zapChannels,
            currentStreamId = streamId.toIntOrNull(),
            onPick = { channel ->
                showChannels = false
                zapTo(channel)
            },
            onDismiss = { showChannels = false },
        )
    }
    if (showSleep) {
        TvSleepDialog(
            selected = sleepMinutes,
            onSelect = { minutes ->
                showSleep = false
                setSleep(minutes)
            },
            onDismiss = { showSleep = false },
        )
    }
    if (showOptions) {
        TvPlaybackOptionsDialog(onDismiss = { showOptions = false })
    }
}

/** Etiqueta pequeña de calidad (p. ej. "1920x1080"), como en la referencia. */
@Composable
private fun TvQualityBadge(text: String, bg: Color = Color(0x66FFFFFF)) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/** Botón de texto del controlador (foco visible con D-pad). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPlayerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, modifier = modifier) { Text(label) }
}

/**
 * Barra de progreso enfocable: con foco, izquierda/derecha salta ∓10 s.
 * El foco se resalta engrosando la barra.
 */
@Composable
private fun TvPlayerSeekBar(
    progress: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val barH = if (focused) 10.dp else 6.dp
    Box(
        modifier = modifier
            .height(44.dp)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onSeek(-10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onSeek(10_000L)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barH)
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(4.dp)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(barH)
                .background(
                    if (focused) Color.White else MtvRed,
                    RoundedCornerShape(4.dp),
                ),
        )
    }
}

/** mm:ss o h:mm:ss. */
private fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "AAC · 5.1" a partir de la pista de audio seleccionada ("" si no hay). */
private fun selectedAudioBadge(p: ExoPlayer): String {
    for (group in p.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val f = group.getTrackFormat(i)
            val codec = audioCodecLabel(f.sampleMimeType)
            val ch = audioChannelsLabel(f.channelCount)
            return if (ch.isNotBlank()) "$codec · $ch" else codec
        }
    }
    return ""
}

private fun audioCodecLabel(mime: String?): String = when {
    mime == null -> "Audio"
    mime.contains("ec-3") || mime == "audio/eac3" -> "E-AC3"
    mime.contains("ac-3") || mime == "audio/ac3" -> "AC3"
    mime.contains("truehd") -> "TrueHD"
    mime.contains("dts") -> "DTS"
    mime.contains("mp4a") -> "AAC"
    mime.contains("opus") -> "Opus"
    mime.contains("vorbis") -> "Vorbis"
    mime.contains("flac") -> "FLAC"
    mime.contains("mpeg") -> "MP3"
    mime.contains("pcm") || mime.contains("raw") -> "PCM"
    else -> mime.substringAfterLast('/').uppercase().take(8)
}

private fun audioChannelsLabel(channels: Int): String = when {
    channels >= 8 -> "7.1"
    channels >= 6 -> "5.1"
    channels == 2 -> "STEREO"
    channels == 1 -> "MONO"
    channels > 0 -> "$channels CH"
    else -> ""
}

// ---------------- Diálogos con foco visible (D-pad) ----------------

/** Fila seleccionable con resaltado de foco para el control remoto. */
@Composable
fun TvTrackRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = when {
            focused || isSelected -> Color.White
            else -> MtvOnBg
        },
        style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(
                when {
                    focused -> MtvRed.copy(alpha = 0.45f)
                    isSelected -> MtvSurfaceVariant
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MtvRed else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(14.dp),
    )
}

@Composable
fun TvAudioTrackDialog(manager: PlayerManager, epoch: Int, onDismiss: () -> Unit) {
    // epoch: si el player se reconstruyó, releer las pistas de la instancia nueva.
    val options = remember(epoch) { manager.audioTracks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pista de audio") },
        text = {
            LazyColumn {
                item {
                    TvTrackRow("Automática", isSelected = false) {
                        manager.clearAudioOverride()
                        onDismiss()
                    }
                }
                items(options) { opt ->
                    TvTrackRow(opt.label, isSelected = false) {
                        manager.selectAudio(opt.groupIndex, opt.trackIndex)
                        onDismiss()
                    }
                }
                if (options.isEmpty()) {
                    item {
                        Text(
                            "No hay pistas de audio alternativas.",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun TvSubtitleTrackDialog(
    manager: PlayerManager,
    epoch: Int,
    subtitleSize: String,
    onSizeSelect: (String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val options = remember(epoch) { manager.textTracks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtítulos") },
        text = {
            LazyColumn {
                item {
                    TvTrackRow("Desactivados", isSelected = false) {
                        manager.disableText()
                        onDismiss()
                    }
                }
                items(options) { opt ->
                    TvTrackRow(opt.label, isSelected = false) {
                        manager.selectText(opt.groupIndex, opt.trackIndex)
                        onDismiss()
                    }
                }
                if (options.isEmpty()) {
                    item {
                        Text(
                            "Este video no trae subtítulos.",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                // Buscar subtítulos por internet (por TMDB ID, igual que el móvil).
                // Solo se muestra cuando hay TMDB ID (películas/series).
                onSearchClick?.let { search ->
                    item {
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .onFocusChanged { focused = it.isFocused }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = search,
                                )
                                .background(
                                    if (focused) MtvRed.copy(alpha = 0.45f) else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 0.dp,
                                    color = if (focused) MtvRed else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp),
                            )
                            Text(
                                "Buscar subtítulos",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Tamaño del texto",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        listOf("S", "M", "L").forEach { size ->
                            val label = when (size) {
                                "S" -> "Pequeño"
                                "L" -> "Grande"
                                else -> "Mediano"
                            }
                            FilterChip(
                                selected = subtitleSize == size,
                                onClick = { onSizeSelect(size) },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Lista de canales para zapping (logo + nombre), navegable con D-pad. */
@Composable
private fun TvChannelListDialog(
    channels: List<ZapChannel>,
    currentStreamId: Int?,
    onPick: (ZapChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canales") },
        text = {
            if (channels.isEmpty()) {
                Text("No hay canales en la lista.", modifier = Modifier.padding(12.dp))
            } else {
                LazyColumn {
                    items(channels, key = { it.streamId }) { channel ->
                        var focused by remember { mutableStateOf(false) }
                        val selected = channel.streamId == currentStreamId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focused = it.isFocused }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onPick(channel) },
                                )
                                .background(
                                    when {
                                        focused -> MtvRed.copy(alpha = 0.45f)
                                        selected -> MtvSurfaceVariant
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 0.dp,
                                    color = if (focused) MtvRed else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(10.dp),
                        ) {
                            if (channel.icon.isNotBlank()) {
                                MtvAsyncImage(
                                    model = channel.icon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    maxSizePx = 128,
                                    modifier = Modifier.size(56.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                channel.name,
                                color = if (focused || selected) Color.White else MtvOnBg,
                                style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Sleep timer: al vencer llama onClose (termina la reproducción). */
@Composable
private fun TvSleepDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(0, 15, 30, 60, 90, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagado automático") },
        text = {
            LazyColumn {
                items(options) { minutes ->
                    val label = if (minutes == 0) "Desactivado" else "$minutes minutos"
                    TvTrackRow(label, isSelected = minutes == selected) {
                        onSelect(minutes)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Resumen rápido de los ajustes de reproducción vigentes. */
@Composable
private fun TvPlaybackOptionsDialog(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val videoDec by container.userPrefs.videoDecoder.collectAsState(initial = "hw")
    val audioDec by container.userPrefs.audioDecoder.collectAsState(initial = "auto")
    val buffer by container.userPrefs.bufferSize.collectAsState(initial = "medio")
    val aspect by container.userPrefs.aspectRatio.collectAsState(initial = "fit")
    val afr by container.userPrefs.afrEnabled.collectAsState(initial = false)
    val surround by container.userPrefs.surroundDefault.collectAsState(initial = false)

    fun videoLabel(v: String) = when (v) {
        "sw" -> "Software"
        else -> "Hardware"
    }
    fun audioLabel(v: String) = when (v) {
        "hw" -> "Hardware"
        "sw" -> "Solo software (FFmpeg)"
        else -> "Automático"
    }
    fun bufferLabel(v: String) = when (v) {
        "pequeno" -> "Pequeño"
        "grande" -> "Grande"
        else -> "Medio"
    }
    fun aspectLabel(v: String) = when (v) {
        "fill" -> "Llenar"
        "zoom" -> "Zoom"
        else -> "Ajustar"
    }

    val rows = listOf(
        "Decodificador de video" to videoLabel(videoDec),
        "Decodificador de audio" to audioLabel(audioDec),
        "Tamaño del buffer" to bufferLabel(buffer),
        "Aspecto" to aspectLabel(aspect),
        "Auto frame rate (AFR)" to if (afr) "Activado" else "Desactivado",
        "Audio envolvente por defecto" to if (surround) "Activado" else "Desactivado",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opciones de reproducción") },
        text = {
            Column {
                rows.forEach { (k, v) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            k,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MtvOnBg,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            v,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
                }
                Text(
                    "Los cambios de decodificador y buffer se aplican al iniciar la próxima reproducción.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MtvOnBg,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun tvLanguageLabel(code: String): String = when (code.lowercase()) {
    "es" -> "Español"
    "en" -> "Inglés"
    "pt" -> "Portugués"
    "fr" -> "Francés"
    else -> code.ifBlank { "Otro idioma" }
}

private fun tvDownloadsLabel(count: Int): String {
    if (count <= 0) return "sin datos de descargas"
    val n = NumberFormat.getInstance(Locale.US).format(count)
    return "$n descargas"
}

/**
 * Búsqueda de subtítulos en OpenSubtitles por TMDB ID (no por nombre) con
 * filas navegables por D-pad. Igual que el diálogo móvil.
 */
@Composable
fun TvSubtitleSearchDialog(
    apiKey: String,
    tmdbId: Int,
    season: Int?,
    episode: Int?,
    onSubtitleReady: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val client = remember(container) { OpenSubtitlesClient(container.httpClientProvider) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var downloadingId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(apiKey, tmdbId, season, episode) {
        loading = true
        error = null
        results = emptyList()
        if (apiKey.isBlank()) {
            loading = false
            error = "Configura tu API key de OpenSubtitles en Ajustes → Subtítulos."
            return@LaunchedEffect
        }
        try {
            val found = client.searchSubtitles(apiKey.trim(), tmdbId, season, episode)
            results = found
            if (found.isEmpty()) {
                error = "No se encontraron subtítulos en español para este contenido."
            }
        } catch (e: Exception) {
            error = "Error buscando subtítulos: ${e.message ?: "revisa tu conexión"}"
        }
        loading = false
    }

    fun download(result: SubtitleResult) {
        if (downloadingId != null) return
        downloadingId = result.fileId
        scope.launch {
            try {
                val file = client.downloadSubtitle(apiKey.trim(), result.fileId, context.cacheDir)
                onSubtitleReady(file)
            } catch (e: Exception) {
                error = "No se pudo descargar el subtítulo: ${e.message ?: "inténtalo de nuevo"}"
            }
            downloadingId = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (downloadingId == null) onDismiss() },
        title = { Text("Buscar subtítulos") },
        text = {
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
                results.isNotEmpty() -> {
                    LazyColumn {
                        items(results, key = { it.fileId }) { r ->
                            val busy = downloadingId == r.fileId
                            var focused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focused = it.isFocused }
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { download(r) },
                                    )
                                    .background(
                                        if (focused) MtvRed.copy(alpha = 0.45f) else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .border(
                                        width = if (focused) 2.dp else 0.dp,
                                        color = if (focused) MtvRed else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .padding(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        tvLanguageLabel(r.language),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(4.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = "Descargar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (r.release.isNotBlank()) {
                                    Text(
                                        r.release,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                Text(
                                    tvDownloadsLabel(r.downloadCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        error ?: "Sin resultados.",
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (!loading && error != null && results.isNotEmpty()) {
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
