
package com.mtv.iptv.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.ImPosterCard
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.PrimaryButton
import com.mtv.iptv.ui.common.ScreenTopBar
import com.mtv.iptv.ui.common.SortMenuButton
import com.mtv.iptv.ui.common.parseRating
import com.mtv.iptv.ui.common.sortCatalogItems
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ventana de renderizado: jamás se dibujan todos los items de una vez. */
private const val PAGE_SIZE = 200

/**
 * "Ver todo" de series: grilla paginada por ventanas de ~200 items con botón
 * "Cargar más". Mismo patrón que AllMoviesScreen: el catálogo completo vive
 * en memoria pero solo se renderiza la ventana visible; filtrado y orden
 * (v1.1) corren con Dispatchers.Default.
 */
@Composable
fun AllSeriesScreen(
    onBack: () -> Unit,
    onSeries: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var all by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }
    var filtered by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortKey by remember { mutableStateOf("nombre") }
    var visibleCount by remember { mutableStateOf(PAGE_SIZE) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        try {
            sortKey = container.userPrefs.sortSeries.first()
            all = repo.getSeries(null)
        } catch (e: Exception) {
            error = "No se pudieron cargar las series."
        }
        loading = false
    }

    LaunchedEffect(all, query, sortKey) {
        filtered = withContext(Dispatchers.Default) {
            val q = query.trim().lowercase()
            val f = if (q.isBlank()) all else all.filter { it.name.lowercase().contains(q) }
            sortCatalogItems(f, sortKey, { it.name }, { it.added }, { it.rating })
        }
        visibleCount = PAGE_SIZE
    }

    fun changeSort(value: String) {
        sortKey = value
        scope.launch {
            try {
                container.userPrefs.setSortSeries(value)
            } catch (e: Exception) {
            }
        }
    }

    val visible = filtered.take(visibleCount)

    MtvUiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MtvBg),
        ) {
            ScreenTopBar(title = "All Series (${all.size})", onBack = onBack) {
                SortMenuButton(sortKey = sortKey, onSortChange = ::changeSort)
            }
            when {
                loading -> LoadingBox(Modifier.weight(1f))
                error != null -> ErrorBox(
                    message = error!!,
                    onRetry = { reloadTick++ },
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar serie") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visible, key = { "${it.seriesId}:${it.name}" }) { v ->
                            ImPosterCard(
                                imageUrl = v.cover.ifBlank { null },
                                title = v.name,
                                rating = parseRating(v.rating),
                                onClick = { onSeries(v.seriesId) },
                                cardWidth = 110.dp,
                            )
                        }
                        if (visibleCount < filtered.size) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        "Mostrando $visibleCount de ${filtered.size}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MtvOnVariant,
                                    )
                                    PrimaryButton(
                                        text = "Cargar más",
                                        onClick = { visibleCount += PAGE_SIZE },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
