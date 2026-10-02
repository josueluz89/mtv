
package com.mtv.iptv.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.ImPosterCard
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvUiTheme
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

/**
 * "Ver todo" de películas: grilla con el catálogo COMPLETO (sin paginación).
 * El catálogo vive en memoria (una sola llamada, como v1.1); LazyVerticalGrid
 * solo compone los items visibles, así que los ~86k items no se dibujan de
 * una vez. El filtrado y el orden (v1.1: nombre/año/rating/recientes) corren
 * con Dispatchers.Default.
 */
@Composable
fun AllMoviesScreen(
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var all by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var filtered by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortKey by remember { mutableStateOf("nombre") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        try {
            sortKey = container.userPrefs.sortVod.first()
            all = repo.getVodStreams(null)
        } catch (e: Exception) {
            error = "No se pudieron cargar las películas."
        }
        loading = false
    }

    LaunchedEffect(all, query, sortKey) {
        filtered = withContext(Dispatchers.Default) {
            val q = query.trim().lowercase()
            val f = if (q.isBlank()) all else all.filter { it.name.lowercase().contains(q) }
            sortCatalogItems(f, sortKey, { it.name }, { it.added }, { it.rating })
        }
    }

    fun changeSort(value: String) {
        sortKey = value
        scope.launch {
            try {
                container.userPrefs.setSortVod(value)
            } catch (e: Exception) {
            }
        }
    }

    MtvUiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MtvBg),
        ) {
            ScreenTopBar(title = "All Movies (${all.size})", onBack = onBack) {
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
                        label = { Text("Buscar película") },
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
                        items(filtered, key = { "${it.streamId}:${it.name}" }) { v ->
                            ImPosterCard(
                                imageUrl = v.streamIcon.ifBlank { null },
                                title = v.name,
                                rating = parseRating(v.rating),
                                onClick = { onVod(v.streamId) },
                                cardWidth = 110.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
