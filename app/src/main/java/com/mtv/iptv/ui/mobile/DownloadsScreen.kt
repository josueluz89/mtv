package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtv.iptv.PlayerActivity
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.downloads.DownloadEntry
import com.mtv.iptv.player.downloads.DownloadQuality
import com.mtv.iptv.player.downloads.EstadoDescarga
import kotlinx.coroutines.launch

/** "123456789" -> "117 MB" / "1.2 GB". */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) "%.1f GB".format(gb) else "%d MB".format(bytes / (1024 * 1024))
}

/**
 * Botón de descarga con estado: Download / progreso con % / Check / error.
 * [id] es el mediaKey ("vod:123", "ep:456"...). Si [adaptive] es true muestra
 * el diálogo de calidad antes de descargar.
 */
@Composable
fun DownloadButton(
    id: String,
    url: String,
    title: String,
    imageUrl: String,
    adaptive: Boolean,
) {
    val container = LocalAppContainer.current
    val module = container.downloadModule
    val scope = rememberCoroutineScope()
    val estado by module.tracker.estadoFlow(id).collectAsState(initial = EstadoDescarga.NoDescargado)
    var showQuality by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    fun start(quality: DownloadQuality) {
        scope.launch {
            starting = true
            module.startDownload(url, id, title, imageUrl, quality)
            starting = false
        }
    }

    fun onTap() {
        if (adaptive) showQuality = true else start(DownloadQuality.AUTOMATICA)
    }

    when (val e = estado) {
        is EstadoDescarga.Descargado -> IconButton(onClick = {}) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Descargado",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        is EstadoDescarga.Descargando -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp),
        ) {
            CircularProgressIndicator(
                progress = e.progreso / 100f,
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp,
            )
            Text("${e.progreso}%", style = MaterialTheme.typography.labelSmall)
        }
        is EstadoDescarga.Error -> IconButton(onClick = { onTap() }) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Reintentar descarga",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        EstadoDescarga.NoDescargado -> IconButton(onClick = { onTap() }, enabled = !starting) {
            Icon(Icons.Default.Download, contentDescription = "Descargar")
        }
    }

    if (showQuality) {
        AlertDialog(
            onDismissRequest = { showQuality = false },
            title = { Text("Calidad de descarga") },
            text = {
                Column {
                    listOf(
                        DownloadQuality.AUTOMATICA to "Automática",
                        DownloadQuality.ALTA to "Alta (hasta 1080p)",
                        DownloadQuality.MEDIA to "Media (hasta 720p)",
                        DownloadQuality.BAJA to "Baja (hasta 480p)",
                    ).forEach { (quality, label) ->
                        TextButton(
                            onClick = { showQuality = false; start(quality) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuality = false }) { Text("Cancelar") }
            },
        )
    }
}

// ---------------- Pantalla "Mis descargas" ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val module = container.downloadModule
    val scope = rememberCoroutineScope()
    val entradas by module.tracker.entradas.collectAsState()
    val espacio = remember(entradas) { module.usedSpaceBytes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis descargas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Espacio usado: ${formatBytes(espacio)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (entradas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No tenés descargas todavía",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entradas, key = { it.id }) { entry ->
                        DownloadRow(
                            entry = entry,
                            onPlay = {
                                PlayerActivity.start(
                                    context, entry.url, entry.title, entry.id, entry.imageUrl
                                )
                            },
                            onDelete = { module.removeDownload(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = entry.imageUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                when (val e = entry.estado) {
                    is EstadoDescarga.Descargando -> {
                        LinearProgressIndicator(
                            progress = e.progreso / 100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Descargando… ${e.progreso}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    EstadoDescarga.Descargado -> Text(
                        "Descargado • ${formatBytes(entry.bytesDescargados)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    EstadoDescarga.Error -> Text(
                        "Error en la descarga",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    EstadoDescarga.NoDescargado -> {}
                }
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar descarga")
            }
        }
    }
}
