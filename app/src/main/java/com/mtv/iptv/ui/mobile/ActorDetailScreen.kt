package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.mtv.iptv.data.remote.tmdb.PersonDetail
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbCreditItem
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Ficha de actor/actriz (solo móvil): foto, datos, biografía y filmografía.
 * Tocar un título lo busca en el servidor Xtream y abre su detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorDetailScreen(
    personId: Int,
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var person by remember { mutableStateOf<PersonDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(personId) {
        loading = true
        person = try {
            container.tmdbRepository.getPerson(personId)
        } catch (e: Exception) {
            null
        }
        loading = false
    }

    fun openCredit(item: TmdbCreditItem) {
        val title = TitleCleaner.clean(item.displayTitle()).title
        if (title.isBlank()) return
        scope.launch {
            try {
                if (item.mediaType == "tv") {
                    val match = repo.findSeriesByTitle(title)
                    if (match != null) onSeries(match.seriesId)
                    else snackbarHostState.showSnackbar("No está disponible en tu servidor.")
                } else {
                    val match = repo.findVodByTitle(title)
                    if (match != null) onVod(match.streamId)
                    else snackbarHostState.showSnackbar("No está disponible en tu servidor.")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("No se pudo buscar en tu servidor.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.details?.name ?: "Actor") },
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
            person == null -> ErrorBox(
                message = "No se pudo cargar la información.",
                onRetry = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val d = person!!.details
                val filmo = person!!.filmography
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
                        verticalAlignment = Alignment.Top,
                    ) {
                        AsyncImage(
                            model = TmdbClient.profileUrl(d.profilePath),
                            contentDescription = d.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            if (d.birthday.isNotBlank()) {
                                Text(
                                    "Nacimiento: ${d.birthday}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (d.placeOfBirth.isNotBlank()) {
                                Text(
                                    d.placeOfBirth,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (d.biography.isNotBlank()) {
                        Text(
                            "Biografía",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            d.biography,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (filmo.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Filmografía",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        // Grid dentro de la columna con scroll: altura fija contenida.
                        Box(modifier = Modifier.height(480.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(110.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filmo, key = { "${it.id}:${it.mediaType}" }) { item ->
                                    PosterCard(
                                        imageUrl = TmdbClient.posterUrl(item.posterPath),
                                        title = item.displayTitle(),
                                        onClick = { openCredit(item) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
