@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.mtv.iptv.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.XtreamCategory
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.CatalogHero
import com.mtv.iptv.ui.common.ImPosterCard
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.RowTitle
import com.mtv.iptv.ui.common.ScreenTopBar
import com.mtv.iptv.ui.common.SectionHeaderRow
import com.mtv.iptv.ui.common.SortMenuButton
import com.mtv.iptv.ui.common.buildMeta
import com.mtv.iptv.ui.common.catalogSortLabel
import com.mtv.iptv.ui.common.openYoutube
import com.mtv.iptv.ui.common.parseRating
import com.mtv.iptv.ui.common.sortCatalogItems
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rutas que exponen las pantallas de películas para que el coordinador las registre. */
object MoviesRoutes {
    const val MOVIES = "v12_movies"
    const val ALL_MOVIES = "v12_all_movies"
}

/** Primeras N categorías que se muestran como filas en la pantalla principal. */
private const val MAX_CATEGORY_ROWS = 6

/** Items por fila horizontal. */
private const val ROW_ITEMS = 20

private data class VodCategoryRow(
    val category: XtreamCategory,
    val total: Int,
    val items: List<XtreamVodStream>,
)

/**
 * Pantalla principal de películas estilo iMPlayer: hero del #1 (backdrop del
 * mejor rankeado según el orden activo), encabezado "All Movies (N)" con botón
 * "Ver todo" (grilla paginada), fila "Mejor valoradas" y filas por categoría.
 *
 * El ordenamiento del catálogo es el de v1.1 (nombre/año/rating/recientes),
 * persistido en UserPrefs, pero ejecutado con Dispatchers.Default: con ~86k
 * items, ordenarlo en el hilo UI congelaría la app.
 */
@Composable
fun MoviesScreen(
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeeAll: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var all by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var sorted by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var topRated by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var categoryRows by remember { mutableStateOf<List<VodCategoryRow>>(emptyList()) }
    var heroItem by remember { mutableStateOf<XtreamVodStream?>(null) }
    var heroTmdb by remember { mutableStateOf<TmdbMedia?>(null) }
    var sortKey by remember { mutableStateOf("nombre") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    // Carga: categorías + catálogo completo (una sola llamada, como v1.1).
    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        try {
            sortKey = container.userPrefs.sortVod.first()
            categories = repo.getVodCategories().distinctBy { it.categoryId }
            all = repo.getVodStreams(null)
        } catch (e: Exception) {
            error = "No se pudieron cargar las películas."
        }
        loading = false
    }

    // Orden + "mejor valoradas" + filas por categoría: fuera del hilo UI.
    LaunchedEffect(all, categories, sortKey) {
        if (all.isEmpty()) {
            sorted = emptyList()
            topRated = emptyList()
            categoryRows = emptyList()
            return@LaunchedEffect
        }
        val s = withContext(Dispatchers.Default) {
            sortCatalogItems(all, sortKey, { it.name }, { it.added }, { it.rating })
        }
        sorted = s
        topRated = withContext(Dispatchers.Default) {
            all.mapNotNull { v -> parseRating(v.rating)?.let { v to it } }
                .sortedByDescending { it.second }
                .take(ROW_ITEMS)
                .map { it.first }
        }
        categoryRows = withContext(Dispatchers.Default) {
            categories.take(MAX_CATEGORY_ROWS).map { cat ->
                val inCat = s.filter { it.categoryId == cat.categoryId }
                VodCategoryRow(cat, inCat.size, inCat.take(ROW_ITEMS))
            }.filter { it.total > 0 }
        }
    }

    // Hero: TMDB del #1 del orden activo.
    LaunchedEffect(sorted) {
        val first = sorted.firstOrNull()
        heroItem = first
        heroTmdb = if (first != null) {
            try {
                container.tmdbRepository.findMovie(first.name, TitleCleaner.clean(first.name).year)
            } catch (e: Exception) {
                null
            }
        } else {
            null
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
            ScreenTopBar(title = "Películas", onBack = onBack) {
                SortMenuButton(sortKey = sortKey, onSortChange = ::changeSort)
            }
            when {
                loading -> LoadingBox(Modifier.weight(1f))
                error != null -> ErrorBox(
                    message = error!!,
                    onRetry = { reloadTick++ },
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val hero = heroItem
                    if (hero != null) {
                        item {
                            val tmdb = heroTmdb
                            CatalogHero(
                                backdropUrl = tmdb?.backdropUrl
                                    ?: hero.streamIcon.ifBlank { null },
                                badge = "N.° 1 · ${catalogSortLabel(sortKey)}",
                                title = tmdb?.title ?: hero.name,
                                meta = buildMeta(
                                    tmdb?.year ?: TitleCleaner.clean(hero.name).year,
                                    tmdb?.rating?.takeIf { it > 0 }
                                        ?: parseRating(hero.rating),
                                    null,
                                ),
                                overview = tmdb?.overview?.takeIf { it.isNotBlank() },
                                onPlay = { onVod(hero.streamId) },
                                onTrailer = tmdb?.trailerKey?.let { key ->
                                    { openYoutube(context, key) }
                                },
                            )
                        }
                    }
                    item {
                        SectionHeaderRow(
                            title = "All Movies (${all.size})",
                            onSeeAll = onSeeAll,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (topRated.isNotEmpty()) {
                        item { RowTitle("Mejor valoradas") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(topRated, key = { "${it.streamId}:${it.name}" }) { v ->
                                    ImPosterCard(
                                        imageUrl = v.streamIcon.ifBlank { null },
                                        title = v.name,
                                        rating = parseRating(v.rating),
                                        onClick = { onVod(v.streamId) },
                                    )
                                }
                            }
                        }
                    }
                    categoryRows.forEach { row ->
                        item {
                            RowTitle("${row.category.categoryName} (${row.total})")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(row.items, key = { "${it.streamId}:${it.name}" }) { v ->
                                    ImPosterCard(
                                        imageUrl = v.streamIcon.ifBlank { null },
                                        title = v.name,
                                        rating = parseRating(v.rating),
                                        onClick = { onVod(v.streamId) },
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
