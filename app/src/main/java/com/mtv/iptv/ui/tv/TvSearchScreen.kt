package com.mtv.iptv.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Buscador en TV con resultados agrupados (Canales, Películas, Series).
 * Todo navegable con D-pad: el campo usa OutlinedTextField de material3
 * (funciona con el control remoto, igual que el login) y los resultados son
 * Cards de tv.material3 con estados de foco visibles.
 *
 * Usa las mismas funciones del data layer que el buscador móvil:
 * searchLive / searchVod / searchSeries (cache en memoria por sesión).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvSearchScreen(
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var liveResults by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var vodResults by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var seriesResults by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }

    fun doSearch() {
        val q = query.trim()
        if (q.length < 2 || searching) return
        searching = true
        error = null
        scope.launch {
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
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Atrás") }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Buscar",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Película, serie o canal…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = ::doSearch, enabled = !searching) {
                    Text(if (searching) "Buscando…" else "Buscar")
                }
            }
        }
        if (error != null) {
            item { Text(error!!, style = MaterialTheme.typography.headlineSmall) }
        } else if (searched && liveResults.isEmpty() && vodResults.isEmpty() && seriesResults.isEmpty()) {
            item {
                Text(
                    "Sin resultados para \"$query\"",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            if (liveResults.isNotEmpty()) {
                item { Text("Canales (${liveResults.size})", style = MaterialTheme.typography.headlineSmall) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(liveResults, key = { "live:${it.streamId}" }) { s ->
                            TvPosterCard(
                                rating = null,
                                title = s.name,
                                imageUrl = s.streamIcon.ifBlank { null },
                                onClick = {
                                ExternalPlayer.play(context, container, repo.liveUrl(s.streamId), s.name,
                                    "live:${s.streamId}", s.streamIcon,
                                )
                                },
                            )
                        }
                    }
                }
            }
            if (vodResults.isNotEmpty()) {
                item { Text("Películas (${vodResults.size})", style = MaterialTheme.typography.headlineSmall) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(vodResults, key = { "vod:${it.streamId}" }) { v ->
                            TvPosterCard(
                                rating = null,
                                title = v.name,
                                imageUrl = v.streamIcon.ifBlank { null },
                                onClick = {
                                onVod(v.streamId)
                                },
                            )
                        }
                    }
                }
            }
            if (seriesResults.isNotEmpty()) {
                item { Text("Series (${seriesResults.size})", style = MaterialTheme.typography.headlineSmall) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(seriesResults, key = { "series:${it.seriesId}" }) { s ->
                            TvPosterCard(
                                rating = null,
                                title = s.name,
                                imageUrl = s.cover.ifBlank { null },
                                onClick = {
                                onSeries(s.seriesId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
