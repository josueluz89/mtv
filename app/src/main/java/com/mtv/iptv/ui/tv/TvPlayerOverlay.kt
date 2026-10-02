package com.mtv.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.mtv.iptv.data.remote.subs.OpenSubtitlesClient
import com.mtv.iptv.data.remote.subs.SubtitleResult
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.PlayerManager
import com.mtv.iptv.player.attachExternalSubtitle
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvRed
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

// ---------------- Overlay del reproductor (TV) ----------------

/**
 * Capa de opciones del reproductor en TV: título + botones (Audio,
 * Subtítulos, −10 s, +10 s). Se muestra y oculta JUNTO con el controlador
 * del PlayerView (el activity sincroniza [visible] con
 * setControllerVisibilityListener). Cuando no hay nada que mostrar, la vista
 * Android que hospeda este overlay se pone en GONE para que nunca robe el
 * foco del D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerOverlay(
    title: String,
    visible: Boolean,
    isLive: Boolean,
    manager: PlayerManager,
    subTmdbId: Int?,
    subSeason: Int?,
    subEpisode: Int?,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showSubSearch by remember { mutableStateOf(false) }
    val subtitleSize by container.userPrefs.subtitleSize.collectAsState(initial = "M")
    val openSubtitlesKey by container.userPrefs.openSubtitlesKey.collectAsState(initial = "")

    val anyDialog = showAudio || showSubs || showSubSearch
    LaunchedEffect(visible, anyDialog) {
        view.visibility =
            if (visible || anyDialog) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun seekBy(deltaMs: Long) {
        val p = manager.player
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        val dur = p.duration
        p.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
    }

    Box(Modifier.fillMaxSize()) {
        if (visible && title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(28.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (visible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { showAudio = true }) { Text("Audio") }
                Button(onClick = { showSubs = true }) { Text("Subtítulos") }
                if (!isLive) {
                    Button(onClick = { seekBy(-10_000L) }) { Text("−10 s") }
                    Button(onClick = { seekBy(10_000L) }) { Text("+10 s") }
                }
            }
        }
    }

    if (showAudio) {
        TvAudioTrackDialog(manager = manager, onDismiss = { showAudio = false })
    }
    if (showSubs) {
        TvSubtitleTrackDialog(
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
fun TvAudioTrackDialog(manager: PlayerManager, onDismiss: () -> Unit) {
    val options = remember { manager.audioTracks() }
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
    subtitleSize: String,
    onSizeSelect: (String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val options = remember { manager.textTracks() }
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
