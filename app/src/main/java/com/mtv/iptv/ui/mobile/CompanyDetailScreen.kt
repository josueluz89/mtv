package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtv.iptv.data.remote.tmdb.CompanyDetail
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbSearchResult
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Ficha de productora (solo móvil): logo, datos y catálogo.
 * Tocar un título lo busca en el servidor Xtream y abre su detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDetailScreen(
    companyId: Int,
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var company by remember { mutableStateOf<CompanyDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        loading = true
        company = try {
            container.tmdbRepository.getCompany(companyId)
        } catch (e: Exception) {
            null
        }
        loading = false
    }

    fun openMovie(item: TmdbSearchResult) {
        val title = TitleCleaner.clean(item.title.ifBlank { item.name }).title
        if (title.isBlank()) return
        scope.launch {
            try {
                val match = repo.findVodByTitle(title)
                if (match != null) onVod(match.streamId)
                else snackbarHostState.showSnackbar("No está disponible en tu servidor.")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("No se pudo buscar en tu servidor.")
            }
        }
    }

    fun openSeries(item: TmdbSearchResult) {
        val title = TitleCleaner.clean(item.title.ifBlank { item.name }).title
        if (title.isBlank()) return
        scope.launch {
            try {
                val match = repo.findSeriesByTitle(title)
                if (match != null) onSeries(match.seriesId)
                else snackbarHostState.showSnackbar("No está disponible en tu servidor.")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("No se pudo buscar en tu servidor.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(company?.details?.name ?: "Productora") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            loading -> LoadingBox(Modifier.padding(padding))
            company == null -> ErrorBox(
                message = "No se pudo cargar la información.",
                onRetry = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val d = company!!.details
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = TmdbClient.posterUrl(d.logoPath),
                            contentDescription = d.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(110.dp)
                                .height(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.headlineSmall)
                            if (d.headquarters.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    d.headquarters,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (d.description.isNotBlank()) {
                        Text(
                            d.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    val movies = company!!.movies
                    if (movies.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Películas",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(movies, key = { "m:${it.id}" }) { item ->
                                PosterCard(
                                    imageUrl = TmdbClient.posterUrl(item.posterPath),
                                    title = item.title.ifBlank { item.name },
                                    onClick = { openMovie(item) },
                                )
                            }
                        }
                    }
                    val series = company!!.series
                    if (series.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Series",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(series, key = { "s:${it.id}" }) { item ->
                                PosterCard(
                                    imageUrl = TmdbClient.posterUrl(item.posterPath),
                                    title = item.title.ifBlank { item.name },
                                    onClick = { openSeries(item) },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
