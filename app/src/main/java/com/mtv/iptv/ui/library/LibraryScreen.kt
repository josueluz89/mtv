package com.mtv.iptv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvGold
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import com.mtv.iptv.ui.common.ScreenTopBar
import com.mtv.iptv.ui.mobile.LoadingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fase 3: pantalla unificada "Mi biblioteca" con pestañas Historial | Favoritos.
 * Punto único para el historial de reproducción y los favoritos (reemplaza a
 * FavoritesScreen como destino de navegación; esa pantalla se conserva intacta).
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScreenTopBar(title = "Mi biblioteca", onBack = onBack)
        TabRow(
            selectedTabIndex = tab,
            containerColor = MtvBg,
            contentColor = MtvOnBg,
        ) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Historial") },
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                selectedContentColor = MtvGold,
                unselectedContentColor = MtvOnVariant,
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Favoritos") },
                icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                selectedContentColor = MtvGold,
                unselectedContentColor = MtvOnVariant,
            )
        }
        when (tab) {
            0 -> HistoryTab()
            1 -> FavoritesTab(onVod = onVod, onSeries = onSeries)
        }
    }
}

@Composable
private fun HistoryTab() {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<PlaybackEntity>?>(null) }

    // Se recarga al entrar a la pestaña (recomposición) y tras cada borrado.
    LaunchedEffect(reload) {
        items = withContext(Dispatchers.IO) {
            container.playbackRepository.recent(50)
        }
    }

    val current = items
    when {
        current == null -> LoadingBox(modifier = Modifier.fillMaxSize())
        current.isEmpty() -> Text(
            "Todavía no hay historial. Lo que reproduzcas aparece aquí.",
            style = MaterialTheme.typography.bodyLarge,
            color = MtvOnVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(current, key = { it.mediaKey }) { entity ->
                val fraction = if (entity.durationMs > 0) {
                    (entity.positionMs.toFloat() / entity.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Card(
                    onClick = {
                        ExternalPlayer.play(
                            context,
                            container,
                            entity.url,
                            entity.name,
                            entity.mediaKey,
                            entity.imageUrl,
                        )
                    },
                    colors = CardDefaults.cardColors(containerColor = MtvSurfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = entity.imageUrl.ifBlank { null },
                            contentDescription = entity.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entity.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MtvOnBg,
                                maxLines = 2,
                            )
                            if (entity.durationMs > 0) {
                                LinearProgressIndicator(
                                    progress = fraction,
                                    color = MtvGold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                )
                            }
                            if (entity.positionMs > 0) {
                                Text(
                                    "Continuar desde ${formatMs(entity.positionMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MtvOnVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                container.playbackRepository.delete(entity.mediaKey)
                                reload++
                            }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Quitar del historial",
                                tint = MtvOnVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository
    val favorites by container.favoritesRepository.observeAll().collectAsState(initial = emptyList())
    var kindFilter by remember { mutableStateOf<String?>(null) }

    val shown = if (kindFilter == null) favorites else favorites.filter { it.kind == kindFilter }

    fun open(fav: FavoriteEntity) {
        when (fav.kind) {
            "live" -> fav.refId.toIntOrNull()?.let {
                ExternalPlayer.play(context, container, repo.liveUrl(it), fav.name, "live:$it", fav.imageUrl)
            }
            "vod" -> fav.refId.toIntOrNull()?.let { onVod(it) }
            "series" -> fav.refId.toIntOrNull()?.let { onSeries(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val filters = listOf(
            null to "Todos",
            "vod" to "Películas",
            "series" to "Series",
            "live" to "En vivo",
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters) { (kind, label) ->
                FilterChip(
                    selected = kindFilter == kind,
                    onClick = { kindFilter = kind },
                    label = { Text(label) },
                )
            }
        }
        if (shown.isEmpty()) {
            Text(
                "Todavía no tenés favoritos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MtvOnVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { "${it.serverId}-${it.kind}-${it.refId}" }) { fav ->
                    Card(
                        onClick = { open(fav) },
                        colors = CardDefaults.cardColors(containerColor = MtvSurfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = fav.imageUrl.ifBlank { null },
                                contentDescription = fav.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    fav.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MtvOnBg,
                                    maxLines = 2,
                                )
                                Text(
                                    kindLabel(fav.kind),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MtvOnVariant,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    container.favoritesRepository.delete(fav)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Quitar de favoritos",
                                    tint = MtvOnVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "live" -> "En vivo"
    "vod" -> "Película"
    "series" -> "Serie"
    else -> kind
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
