package com.mtv.iptv.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.player.PlayerManager
import com.mtv.iptv.ui.mobile.safeClickable
import java.util.Locale

@Composable
fun AudioTrackDialog(manager: PlayerManager, onDismiss: () -> Unit) {
    val options = manager.audioTracks()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pista de audio") },
        text = {
            LazyColumn {
                item {
                    TrackRow("Automática", isSelected = false) {
                        manager.clearAudioOverride()
                        onDismiss()
                    }
                }
                items(options) { opt ->
                    TrackRow(opt.label, isSelected = false) {
                        manager.selectAudio(opt.groupIndex, opt.trackIndex)
                        onDismiss()
                    }
                }
                if (options.isEmpty()) {
                    item { Text("No hay pistas de audio alternativas.", modifier = Modifier.padding(12.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun SubtitleTrackDialog(
    manager: PlayerManager,
    subtitleSize: String,
    onSizeSelect: (String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val options = manager.textTracks()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtítulos") },
        text = {
            LazyColumn {
                item {
                    TrackRow("Desactivados", isSelected = false) {
                        manager.disableText()
                        onDismiss()
                    }
                }
                items(options) { opt ->
                    TrackRow(opt.label, isSelected = false) {
                        manager.selectText(opt.groupIndex, opt.trackIndex)
                        onDismiss()
                    }
                }
                if (options.isEmpty()) {
                    item { Text("Este video no trae subtítulos.", modifier = Modifier.padding(12.dp)) }
                }
                // Buscar subtítulos por internet (el integrador cablea el diálogo
                // de búsqueda con el TMDB ID del contenido en reproducción).
                // Solo se muestra cuando hay TMDB ID (películas/series).
                onSearchClick?.let { search ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .safeClickable(onClick = search),
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

@Composable
fun SpeedDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velocidad") },
        text = {
            LazyColumn {
                items(options) { s ->
                    TrackRow(
                        label = if (s == 1f) "Normal (1x)" else "${s}x",
                        isSelected = s == current,
                    ) { onSelect(s); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun TrackRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable(onClick = onClick)
            .padding(12.dp),
    )
}

@Composable
fun SleepTimerDialog(selectedMinutes: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0, 15, 30, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador de apagado") },
        text = {
            LazyColumn {
                items(options) { m ->
                    TrackRow(
                        label = if (m == 0) "Apagado" else "$m minutos",
                        isSelected = m == selectedMinutes,
                    ) { onSelect(m); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun StreamInfoDialog(manager: PlayerManager, onDismiss: () -> Unit) {
    // Se lee en el hilo principal (los diálogos Compose corren en UI).
    val vf = manager.player.videoFormat
    val resolution = if (vf != null && vf.width > 0 && vf.height > 0) {
        "${vf.width} × ${vf.height}"
    } else {
        "—"
    }
    val codec = vf?.sampleMimeType?.substringAfter("/")?.uppercase(Locale.US)?.takeIf { it.isNotBlank() } ?: "—"
    val fps = if (vf != null && vf.frameRate > 0) "${vf.frameRate.toInt()} fps" else "—"
    val bitrate = when {
        vf == null || vf.bitrate <= 0 -> "—"
        vf.bitrate >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", vf.bitrate / 1_000_000f)
        else -> "${vf.bitrate / 1000} kbps"
    }
    // Datos técnicos de audio (lo que ExoPlayer expone de verdad).
    val af = manager.player.audioFormat
    val audioCodec = af?.sampleMimeType
        ?.substringAfter("/")
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotBlank() } ?: "—"
    val audioChannels = if (af != null && af.channelCount > 0) {
        "${af.channelCount} canales"
    } else {
        "—"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información del stream") },
        text = {
            LazyColumn {
                item { Text("Resolución: $resolution", modifier = Modifier.padding(12.dp)) }
                item { Text("Códec: $codec", modifier = Modifier.padding(12.dp)) }
                item { Text("Cuadros por segundo: $fps", modifier = Modifier.padding(12.dp)) }
                item { Text("Bitrate: $bitrate", modifier = Modifier.padding(12.dp)) }
                item {
                    Text(
                        "Audio",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    )
                }
                item { Text("Códec de audio: $audioCodec", modifier = Modifier.padding(12.dp)) }
                item { Text("Canales de audio: $audioChannels", modifier = Modifier.padding(12.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
