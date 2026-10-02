package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.xtream.XtreamCategory
import com.mtv.iptv.di.LocalAppContainer

fun sectionTitle(kind: String): String = when (kind) {
    "live" -> "En vivo"
    "vod" -> "Películas"
    "series" -> "Series"
    else -> "Explorar"
}

private fun sectionIcon(kind: String): ImageVector = when (kind) {
    "live" -> Icons.Default.LiveTv
    "vod" -> Icons.Default.Movie
    else -> Icons.Default.Tv
}

/**
 * Pantalla de sección: categorías del servidor como tarjetas navegables,
 * más una entrada de Favoritos filtrada a la sección.
 * Tocar una categoría abre la lista de items (CategoryListScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    kind: String,
    onBack: () -> Unit,
    onCategory: (kind: String, categoryId: String, categoryName: String) -> Unit,
    onFavorites: (kind: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(kind, reloadTick) {
        loading = true
        error = null
        try {
            categories = when (kind) {
                "live" -> repo.getLiveCategories()
                "vod" -> repo.getVodCategories()
                else -> repo.getSeriesCategories()
            }.distinctBy { it.categoryId }
        } catch (e: Exception) {
            error = "No se pudieron cargar las categorías."
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sectionTitle(kind)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> LoadingBox(Modifier.padding(padding))
            error != null -> ErrorBox(
                message = error!!,
                onRetry = { reloadTick++ },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    CategoryCard(
                        icon = Icons.Default.Star,
                        title = "Favoritos",
                        subtitle = "Tus ${sectionTitle(kind).lowercase()} guardados",
                        onClick = { onFavorites(kind) },
                    )
                }
                items(categories, key = { "${it.categoryId}:${it.categoryName}" }) { cat ->
                    CategoryCard(
                        icon = sectionIcon(kind),
                        title = cat.categoryName,
                        subtitle = null,
                        onClick = { onCategory(kind, cat.categoryId, cat.categoryName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
