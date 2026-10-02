package com.mtv.iptv.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.player.PlayerManager

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
fun SubtitleTrackDialog(manager: PlayerManager, onDismiss: () -> Unit) {
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
            .clickable(onClick = onClick)
            .padding(12.dp),
    )
}
