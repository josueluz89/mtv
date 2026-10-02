package com.mtv.iptv.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbSearchResult
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import com.mtv.iptv.ui.mobile.SectionHeader
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Inicio estilo iMPlayer (tema oscuro, cards con elevación, hero con
 * degradado): carrusel de tendencias TMDB, Seguir viendo, películas y series
 * agregadas recientemente, y Top canales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
    onLiveTv: () -> Unit,
    onMovies: () -> Unit,
    onAllSeries: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var trending by remember { mutableStateOf<List<TmdbSearchResult>>(emptyList()) }
    var continueWatching by remember { mutableStateOf<List<PlaybackEntity>>(emptyList()) }
    var recentVod by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var recentSeries by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }
    var topChannels by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var resolvingHero by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        if (repo.session == null) {
            onLogout()
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            coroutineScope {
                val trendingJob = async {
                    try {
                        container.tmdbRepository.trendingWeek()
                    } catch (e: Exception) {
                        CrashReporter.log(context, "home", "trending ERROR ${e.message}")
                        emptyList()
                    }
                }
                val watchingJob = async {
                    container.playbackRepository.recent(15).filter { it.url.isNotBlank() }
                }
                // "Agregadas recientemente": se ordena por campo `added` sobre las
                // primeras 3 categorías (no se descarga el catálogo completo).
                val vodJob = async {
                    val cats = repo.getVodCategories().take(3)
                    val merged = mutableListOf<XtreamVodStream>()
                    for (c in cats) merged += repo.getVodStreams(c.categoryId)
                    merged.filter { it.added.isNotBlank() }
                        .distinctBy { it.streamId }
                        .sortedByDescending { it.added.toLongOrNull() ?: 0L }
                        .take(15)
                }
                val seriesJob = async {
                    val cats = repo.getSeriesCategories().take(3)
                    val merged = mutableListOf<XtreamSeries>()
                    for (c in cats) merged += repo.getSeries(c.categoryId)
                    merged.filter { it.added.isNotBlank() }
                        .distinctBy { it.seriesId }
                        .sortedByDescending { it.added.toLongOrNull() ?: 0L }
                        .take(15)
                }
                val liveJob = async {
                    val cats = repo.getLiveCategories()
                    if (cats.isNotEmpty()) repo.getLiveStreams(cats.first().categoryId).take(20)
                    else emptyList()
                }
                trending = trendingJob.await()
                continueWatching = watchingJob.await()
                recentVod = vodJob.await()
                recentSeries = seriesJob.await()
                topChannels = liveJob.await()
            }
        } catch (e: Exception) {
            CrashReporter.log(context, "home", "ERROR ${e::class.java.simpleName}: ${e.message}")
            error = "No se pudo cargar el contenido."
        }
        loading = false
    }

    /** Tap en el hero: resuelve el título TMDB al id Xtream y abre el detalle. */
    fun openHero(item: TmdbSearchResult) {
        if (resolvingHero) return
        resolvingHero = true
        scope.launch {
            try {
                val title = TitleCleaner.clean(item.title.ifBlank { item.name }).title
                if (item.mediaType == "tv") {
                    val s = repo.findSeriesByTitle(title.lowercase())
                    if (s != null) onSeries(s.seriesId)
                    else Toast.makeText(context, "No está en tu servidor", Toast.LENGTH_SHORT).show()
                } else {
                    val v = repo.findVodByTitle(title.lowercase())
                    if (v != null) onVod(v.streamId)
                    else Toast.makeText(context, "No está en tu servidor", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir el detalle", Toast.LENGTH_SHORT).show()
            }
            resolvingHero = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MTV") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onFavorites) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favoritos")
                    }
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "Mis descargas")
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
            ) {
                if (trending.isNotEmpty()) {
                    item {
                        HeroCarousel(
                            items = trending,
                            resolving = resolvingHero,
                            onItemClick = ::openHero,
                        )
                    }
                }
                if (continueWatching.isNotEmpty()) {
                    item { SectionHeader("Seguir viendo") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(continueWatching, key = { it.mediaKey }) { item ->
                                ContinueWatchingHomeCard(
                                    imageUrl = item.imageUrl.ifBlank { null },
                                    name = item.name,
                                    positionMs = item.positionMs,
                                    durationMs = item.durationMs,
                                    onClick = {
                                        ExternalPlayer.play(context, container, item.url, item.name,
                                            item.mediaKey, item.imageUrl,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                if (recentVod.isNotEmpty()) {
                    item { SectionHeader("Agregadas recientemente") { onMovies() } }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recentVod, key = { "${it.streamId}:${it.name}" }) { v ->
                                RatingPosterCard(
                                    imageUrl = v.streamIcon.ifBlank { null },
                                    title = v.name,
                                    rating = parseRating(v.rating),
                                    onClick = { onVod(v.streamId) },
                                )
                            }
                        }
                    }
                }
                if (recentSeries.isNotEmpty()) {
                    item { SectionHeader("Series agregadas recientemente") { onAllSeries() } }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recentSeries, key = { "${it.seriesId}:${it.name}" }) { s ->
                                RatingPosterCard(
                                    imageUrl = s.cover.ifBlank { null },
                                    title = s.name,
                                    rating = parseRating(s.rating),
                                    onClick = { onSeries(s.seriesId) },
                                )
                            }
                        }
                    }
                }
                if (topChannels.isNotEmpty()) {
                    item { SectionHeader("Top canales") { onLiveTv() } }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(topChannels, key = { "${it.streamId}:${it.name}" }) { s ->
                                ChannelLogoCard(
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
                    }
                }
            }
        }
    }
}
