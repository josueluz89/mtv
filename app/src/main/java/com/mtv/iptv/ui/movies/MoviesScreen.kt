package com.mtv.iptv.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.xtream.XtreamCategory
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvGold
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.ScreenTopBar
import com.mtv.iptv.ui.common.SecondaryButton
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import com.mtv.iptv.ui.mobile.safeClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VodFolderRow(
    val category: XtreamCategory,
    val total: Int,
)

/**
 * Pantalla principal de películas: TODAS las categorías VOD del proveedor
 * como carpetas (vista principal), cada una con su conteo real de títulos.
 *
 * Tocar una carpeta abre su contenido COMPLETO. El buscador filtra las
 * carpetas por nombre. Los conteos se calculan con UNA sola llamada
 * `getVodStreams(null)` agrupada por categoryId en Dispatchers.Default.
 * Las categorías vacías se ocultan.
 */
@Composable
fun MoviesScreen(
    onBack: () -> Unit,
    onFolder: (XtreamCategory) -> Unit,
    onSeeAll: () -> Unit,
) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var folders by remember { mutableStateOf<List<VodFolderRow>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        try {
            val categories = repo.getVodCategories().distinctBy { it.categoryId }
            val all = repo.getVodStreams(null)
            folders = withContext(Dispatchers.Default) {
                val counts = all.groupingBy { it.categoryId }.eachCount()
                categories.mapNotNull { cat ->
                    val total = counts[cat.categoryId] ?: 0
                    if (total > 0) VodFolderRow(cat, total) else null
                }
            }
        } catch (e: Exception) {
            error = "No se pudieron cargar las películas."
        }
        loading = false
    }

    val visible = remember(folders, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) folders
        else folders.filter { it.category.categoryName.lowercase().contains(q) }
    }

    MtvUiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MtvBg),
        ) {
            ScreenTopBar(title = "Películas", onBack = onBack)
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
                        label = { Text("Buscar carpeta") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Carpetas (${visible.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MtvOnBg,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = "Ver todo", onClick = onSeeAll)
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(visible, key = { it.category.categoryId }) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .safeClickable { onFolder(row.category) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MtvSurfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MtvGold,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp),
                                ) {
                                    Text(
                                        row.category.categoryName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MtvOnBg,
                                    )
                                    Text(
                                        "${row.total} títulos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MtvOnVariant,
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
