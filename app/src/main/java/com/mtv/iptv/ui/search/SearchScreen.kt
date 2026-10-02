package com.mtv.iptv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtv.iptv.PlayerActivity
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.home.ChannelLogoCard
import com.mtv.iptv.ui.home.RatingPosterCard
import com.mtv.iptv.ui.home.parseRating
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import com.mtv.iptv.ui.mobile.SectionHeader
import kotlinx.coroutines.delay

/**
 * Buscador con resultados agrupados (Canales, Películas, Series).
 *
 * Usa [com.mtv.iptv.data.remote.xtream.XtreamRepository.searchLive],
 * [searchVod] y [searchSeries]: el catálogo completo se descarga una sola vez
 * por sesión (en memoria) y las búsquedas filtran sobre ese cache — no se
 * vuelve a descargar por cada tecla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var liveResults by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var vodResults by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var seriesResults by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }

    // Debounce: busca 500 ms después de la última tecla.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            searching = false
            searched = false
            liveResults = emptyList()
            vodResults = emptyList()
            seriesResults = emptyList()
            error = null
            return@LaunchedEffect
        }
        delay(500)
        searching = true
        error = null
        try {
            liveResults = repo.searchLive(q)
            vodResults = repo.searchVod(q)
            seriesResults = repo.searchSeries(q)
            searched = true
        } catch (e: Exception) {
            error = "No se pudo buscar. Revisá tu conexión."
        }
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar") },
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Película, serie o canal…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
            )
            when {
                error != null -> ErrorBox(
                    message = error!!,
                    onRetry = { val q = query; query = ""; query = q },
                )
                searching -> LoadingBox()
                !searched -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Escribí al menos 2 letras para buscar en canales, películas y series.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                liveResults.isEmpty() && vodResults.isEmpty() && seriesResults.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Sin resultados para \"$query\"",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (liveResults.isNotEmpty()) {
                        item { SectionHeader("Canales (${liveResults.size})") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(liveResults, key = { "live:${it.streamId}" }) { s ->
                                    ChannelLogoCard(
                                        iconUrl = s.streamIcon.ifBlank { null },
                                        name = s.name,
                                        onClick = {
                                            PlayerActivity.start(
                                                context,
                                                repo.liveUrl(s.streamId),
                                                s.name,
                                                "live:${s.streamId}",
                                                s.streamIcon,
                                            )
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (vodResults.isNotEmpty()) {
                        item { SectionHeader("Películas (${vodResults.size})") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(vodResults, key = { "vod:${it.streamId}" }) { v ->
                                    RatingPosterCard(
                                        imageUrl = v.streamIcon.ifBlank { null },
                                        title = v.name,
                                        rating = parseRating(v.rating),
                                        onClick = { onVod(v.streamId) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (seriesResults.isNotEmpty()) {
                        item { SectionHeader("Series (${seriesResults.size})") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(seriesResults, key = { "series:${it.seriesId}" }) { s ->
                                    RatingPosterCard(
                                        imageUrl = s.cover.ifBlank { null },
                                        title = s.name,
                                        rating = parseRating(s.rating),
                                        onClick = { onSeries(s.seriesId) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
