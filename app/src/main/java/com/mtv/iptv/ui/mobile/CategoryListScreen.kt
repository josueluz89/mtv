package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.data.remote.xtream.XtreamCategory
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Año (UTC del sistema) extraído del timestamp unix de "added". */
private fun yearFromAdded(added: String): Long = try {
    java.time.Instant.ofEpochSecond(added.toLong())
        .atZone(java.time.ZoneId.systemDefault()).year.toLong()
} catch (e: Exception) {
    0L
}

/** Aplica el orden elegido a una lista de catálogo (vod/series). */
private fun <T> sortCatalog(
    list: List<T>,
    sortKey: String,
    name: (T) -> String,
    added: (T) -> String,
    rating: (T) -> String,
): List<T> = when (sortKey) {
    "recientes" -> list.sortedByDescending { added(it).toLongOrNull() ?: 0L }
    "rating" -> list.sortedByDescending { rating(it).toDoubleOrNull() ?: 0.0 }
    "anio" -> list.sortedByDescending { yearFromAdded(added(it)) }
    else -> list.sortedBy { name(it).lowercase() }
}

private val sortOptions = listOf(
    "nombre" to "Nombre",
    "recientes" to "Recientes",
    "rating" to "Rating",
    "anio" to "Año",
)

/**
 * Lista genérica por tipo (live | vod | series) con chips de categoría,
 * buscador por texto y grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    kind: String,
    categoryId: String,
    categoryName: String,
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCat by remember(kind) { mutableStateOf(categoryId) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var liveList by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var vodList by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }
    var reloadTick by remember { mutableStateOf(0) }

    // Orden del catálogo (vod/series; live se deja como está).
    var sortKey by remember { mutableStateOf("nombre") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    fun changeSort(value: String) {
        sortKey = value
        sortMenuOpen = false
        scope.launch {
            try {
                if (kind == "series") container.userPrefs.setSortSeries(value)
                else container.userPrefs.setSortVod(value)
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(kind) {
        try {
            sortKey = if (kind == "series") container.userPrefs.sortSeries.first()
                else container.userPrefs.sortVod.first()
        } catch (e: Exception) {
            // default "nombre"
        }
        try {
            categories = when (kind) {
                "live" -> repo.getLiveCategories()
                "vod" -> repo.getVodCategories()
                else -> repo.getSeriesCategories()
            }.distinctBy { it.categoryId }
        } catch (e: Exception) {
            error = "No se pudieron cargar las categorías."
        }
    }

    LaunchedEffect(kind, selectedCat, reloadTick) {
        loading = true
        error = null
        try {
            val cat = selectedCat.takeIf { it != "all" }
            when (kind) {
                "live" -> liveList = repo.getLiveStreams(cat)
                "vod" -> vodList = repo.getVodStreams(cat)
                else -> seriesList = repo.getSeries(cat)
            }
        } catch (e: Exception) {
            error = "No se pudo cargar el contenido."
        }
        loading = false
    }

    val q = query.trim().lowercase()
    // Filtro + orden FUERA del hilo UI: con catálogos de decenas de miles de
    // items, hacerlo en el cuerpo del composable congelaba la UI en cada
    // recomposición (cada tecla del buscador). Se recalcula solo cuando
    // cambian la lista, la query o el criterio de orden.
    val sortedLive by produceState(initialValue = emptyList<XtreamLiveStream>(), liveList, q) {
        value = withContext(Dispatchers.Default) {
            if (q.isBlank()) liveList else liveList.filter { it.name.lowercase().contains(q) }
        }
    }
    val sortedVod by produceState(initialValue = emptyList<XtreamVodStream>(), vodList, q, sortKey) {
        value = withContext(Dispatchers.Default) {
            val f = if (q.isBlank()) vodList else vodList.filter { it.name.lowercase().contains(q) }
            sortCatalog(f, sortKey, { it.name }, { it.added }, { it.rating })
        }
    }
    val sortedSeries by produceState(initialValue = emptyList<XtreamSeries>(), seriesList, q, sortKey) {
        value = withContext(Dispatchers.Default) {
            val f = if (q.isBlank()) seriesList else seriesList.filter { it.name.lowercase().contains(q) }
            sortCatalog(f, sortKey, { it.name }, { it.added }, { it.rating })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName.ifBlank { "Explorar" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (kind != "live") {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Ordenar")
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false },
                            ) {
                                sortOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(if (sortKey == value) "✓ $label" else label) },
                                        onClick = { changeSort(value) },
                                    )
                                }
                            }
                        }
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
                label = { Text("Buscar") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCat == "all",
                        onClick = { selectedCat = "all" },
                        label = { Text("Todo") },
                    )
                }
                items(categories, key = { "${it.categoryId}:${it.categoryName}" }) { cat ->
                    FilterChip(
                        selected = selectedCat == cat.categoryId,
                        onClick = { selectedCat = cat.categoryId },
                        label = { Text(cat.categoryName) },
                    )
                }
            }
            when {
                loading -> LoadingBox(Modifier.weight(1f))
                error != null -> ErrorBox(
                    message = error!!,
                    onRetry = { reloadTick++ },
                    modifier = Modifier.weight(1f),
                )
                kind == "live" -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(sortedLive, key = { "${it.streamId}:${it.name}" }) { s ->
                        ChannelCard(
                            iconUrl = s.streamIcon.ifBlank { null },
                            name = s.name,
                            onClick = {
                                ExternalPlayer.play(context, container,
                                    repo.liveUrl(s.streamId),
                                    s.name,
                                    "live:${s.streamId}",
                                    s.streamIcon,
                                )
                            },
                        )
                    }
                }
                kind == "vod" -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(sortedVod, key = { "${it.streamId}:${it.name}" }) { v ->
                        PosterCard(
                            imageUrl = v.streamIcon.ifBlank { null },
                            title = v.name,
                            onClick = { onVod(v.streamId) },
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(sortedSeries, key = { "${it.seriesId}:${it.name}" }) { s ->
                        PosterCard(
                            imageUrl = s.cover.ifBlank { null },
                            title = s.name,
                            onClick = { onSeries(s.seriesId) },
                        )
                    }
                }
            }
        }
    }
}
