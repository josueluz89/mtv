package com.mtv.iptv.ui.downloads

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.mtv.iptv.ui.mobile.safeClickable
import kotlinx.coroutines.launch

/** Pref de calidad (UserPrefs.dlQuality) -> enum de DownloadQuality. */
private fun qualityFromPref(pref: String): DownloadQuality = when (pref) {
    "alta" -> DownloadQuality.ALTA
    "media" -> DownloadQuality.MEDIA
    "baja" -> DownloadQuality.BAJA
    else -> DownloadQuality.AUTOMATICA
}

private val qualityOptions = listOf(
    "auto" to "Automática",
    "alta" to "Alta",
    "media" to "Media",
    "baja" to "Baja",
)

/**
 * Botón de descarga con estado: Download / progreso con % / Check / error.
 * [id] es el mediaKey ("vod:123", "ep:456"...). Si [adaptive] es true muestra
 * el diálogo de calidad antes de descargar; si no, usa la calidad configurada
 * en Ajustes → Descargas.
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
    val prefQuality by container.userPrefs.dlQuality.collectAsState(initial = "alta")
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
        if (adaptive) showQuality = true else start(qualityFromPref(prefQuality))
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

/**
 * Mis descargas: tarjeta por item (miniatura, título, tamaño, estado),
 * progreso visible de las activas, barra de espacio usado, y acciones:
 * reproducir offline, borrar, y ajuste de calidad/ubicación.
 *
 * La reproducción offline funciona con la misma URL: el reproductor lee del
 * caché compartido de Media3 (DownloadModule), sin usar datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val module = container.downloadModule
    val scope = rememberCoroutineScope()
    val entradas by module.tracker.entradas.collectAsState()
    val dlQuality by container.userPrefs.dlQuality.collectAsState(initial = "alta")

    val espacio = remember(entradas) { module.usedSpaceBytes() }
    val maxEspacio = module.maxCacheBytes
    val snackbarHostState = remember { SnackbarHostState() }

    var qualityDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DownloadEntry?>(null) }

    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ---- Barra de espacio usado ----
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val progress = if (maxEspacio > 0) (espacio.toFloat() / maxEspacio).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Espacio usado: ${formatBytes(espacio)} de ${formatBytes(maxEspacio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Ajustes de descarga ----
            DownloadSettingRow(
                title = "Calidad de descarga",
                subtitle = qualityOptions.firstOrNull { it.first == dlQuality }?.second ?: dlQuality,
                onClick = { qualityDialog = true },
            )
            DownloadSettingRow(
                title = "Ubicación",
                subtitle = "Almacenamiento interno (caché de la app)",
                onClick = null,
            )

            Spacer(Modifier.height(4.dp))

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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(entradas, key = { it.id }) { entry ->
                        DownloadCard(
                            entry = entry,
                            onPlay = {
                                PlayerActivity.start(
                                    context, entry.url, entry.title, entry.id, entry.imageUrl
                                )
                            },
                            onDelete = { confirmDelete = entry },
                        )
                    }
                }
            }
        }
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("Calidad de descarga") },
            text = {
                Column {
                    qualityOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeClickable {
                                    scope.launch { container.userPrefs.setDlQuality(value) }
                                    qualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == dlQuality,
                                onClick = {
                                    scope.launch { container.userPrefs.setDlQuality(value) }
                                    qualityDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { qualityDialog = false }) { Text("Cancelar") }
            },
        )
    }

    val toDelete = confirmDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Borrar descarga") },
            text = { Text("Se borra \"${toDelete.title}\" de este dispositivo. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    module.removeDownload(toDelete.id)
                    showMessage("Descarga borrada")
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DownloadSettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    var rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
    if (onClick != null) rowModifier = rowModifier.safeClickable(onClick = onClick)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tarjeta de una descarga: miniatura, título, tamaño, estado y acciones. */
@Composable
private fun DownloadCard(
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
                            "Descargando… ${e.progreso}% • ${formatBytes(entry.bytesDescargados)}" +
                                sizeSuffix(entry.tamanoTotal),
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
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir sin conexión")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar descarga")
            }
        }
    }
}

/** " de 1.2 GB" si se conoce el tamaño total, "" si no. */
private fun sizeSuffix(total: Long): String =
    if (total > 0) " de ${formatBytes(total)}" else ""
