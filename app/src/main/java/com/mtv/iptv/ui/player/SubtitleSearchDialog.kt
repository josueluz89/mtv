package com.mtv.iptv.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.subs.OpenSubtitlesClient
import com.mtv.iptv.data.remote.subs.SubtitleResult
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.mobile.safeClickable
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

private fun languageLabel(code: String): String = when (code.lowercase()) {
    "es" -> "Español"
    "en" -> "Inglés"
    "pt" -> "Portugués"
    "fr" -> "Francés"
    else -> code.ifBlank { "Otro idioma" }
}

private fun downloadsLabel(count: Int): String {
    if (count <= 0) return "sin datos de descargas"
    val n = NumberFormat.getInstance(Locale.US).format(count)
    return "$n descargas"
}

/**
 * Busca subtítulos en OpenSubtitles por TMDB ID (no por nombre) y descarga
 * el elegido. El integrador pasa el tmdbId (ver reporte): película = ID TMDB
 * de la película; serie = ID TMDB de la serie + temporada/episodio.
 */
@Composable
fun SubtitleSearchDialog(
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .safeClickable(onClick = { download(r) })
                                    .padding(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        languageLabel(r.language),
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
                                    downloadsLabel(r.downloadCount),
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
