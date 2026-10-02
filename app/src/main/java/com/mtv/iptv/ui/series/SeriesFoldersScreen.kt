package com.mtv.iptv.ui.series

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.mtv.iptv.ui.common.MtvSurface
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.ScreenTopBar
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import com.mtv.iptv.ui.mobile.safeClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SeriesFolderRow(
    val category: XtreamCategory,
    val count: Int,
)

/**
 * Carpetas del catálogo de series: lista TODAS las categorías del proveedor
 * como filas tipo carpeta, con el conteo de series de cada una.
 *
 * Los conteos se calculan con UNA sola llamada `repo.getSeries(null)`
 * (cacheada por sesión) agrupada por categoryId en Dispatchers.Default.
 * Las categorías vacías se ocultan.
 */
@Composable
fun SeriesFoldersScreen(
    onBack: () -> Unit,
    onFolder: (XtreamCategory) -> Unit,
) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var rows by remember { mutableStateOf<List<SeriesFolderRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        try {
            val categories = repo.getSeriesCategories().distinctBy { it.categoryId }
            val all = repo.getSeries(null)
            rows = withContext(Dispatchers.Default) {
                val counts = all.groupingBy { it.categoryId }.eachCount()
                categories.mapNotNull { cat ->
                    val n = counts[cat.categoryId] ?: 0
                    if (n > 0) SeriesFolderRow(cat, n) else null
                }.sortedBy { it.category.categoryName.lowercase() }
            }
        } catch (e: Exception) {
            error = "No se pudieron cargar las carpetas."
        }
        loading = false
    }

    MtvUiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MtvBg),
        ) {
            ScreenTopBar(title = "Carpetas de series", onBack = onBack)
            when {
                loading -> LoadingBox(Modifier.weight(1f))
                error != null -> ErrorBox(
                    message = error!!,
                    onRetry = { reloadTick++ },
                    modifier = Modifier.weight(1f),
                )
                rows.isEmpty() -> ErrorBox(
                    message = "No hay carpetas con series.",
                    onRetry = { reloadTick++ },
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.category.categoryId }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MtvSurface)
                                .safeClickable { onFolder(row.category) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MtvGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MtvGold,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp),
                            ) {
                                Text(
                                    row.category.categoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MtvOnBg,
                                )
                                Text(
                                    "${row.count} series",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MtvOnVariant,
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MtvOnVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
