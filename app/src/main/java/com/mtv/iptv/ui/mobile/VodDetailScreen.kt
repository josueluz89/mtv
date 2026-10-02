package com.mtv.iptv.ui.mobile

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtv.iptv.PlayerActivity
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.VodInfoResponse
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

@Composable
fun VodDetailScreen(
    streamId: Int,
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onActor: (Int) -> Unit,
    onCompany: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository
    val session = repo.session

    var info by remember { mutableStateOf<VodInfoResponse?>(null) }
    var tmdb by remember { mutableStateOf<TmdbMedia?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(streamId) {
        loading = true
        try {
            val vi = repo.getVodInfo(streamId)
            info = vi
            tmdb = container.tmdbRepository.findMovie(
                vi.movieData.name,
                TitleCleaner.yearFromDate(vi.info.releasedate),
            )
            if (session != null) {
                isFav = container.favoritesRepository.isFavorite(session.server.id, "vod", streamId.toString())
            }
        } catch (e: Exception) {
            // se muestra lo que haya
        }
        loading = false
    }

    fun play() {
        val vi = info ?: return
        PlayerActivity.start(
            context,
            repo.vodUrl(vi.movieData.streamId, vi.movieData.containerExtension),
            vi.movieData.name,
            "vod:${vi.movieData.streamId}",
            tmdb?.posterUrl ?: vi.info.movieImage,
        )
    }

    fun toggleFavorite() {
        val vi = info ?: return
        val s = session ?: return
        scope.launch {
            isFav = container.favoritesRepository.toggle(
                FavoriteEntity(
                    serverId = s.server.id,
                    kind = "vod",
                    refId = streamId.toString(),
                    name = vi.movieData.name,
                    imageUrl = tmdb?.posterUrl ?: vi.info.movieImage,
                )
            )
        }
    }

    fun openTrailer() {
        val key = tmdb?.trailerKey ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
        )
    }

    fun openSimilar(tmdbId: Int, title: String) {
        scope.launch {
            try {
                val cleaned = TitleCleaner.clean(title).title
                val match = repo.findVodByTitle(cleaned)
                if (match != null) onVod(match.streamId)
                else snackbarHostState.showSnackbar("No está disponible en tu servidor.")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("No se pudo buscar en tu servidor.")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (loading && info == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val vi = info
        val title = tmdb?.title ?: vi?.movieData?.name.orEmpty()
        val poster = tmdb?.posterUrl ?: vi?.info?.movieImage.orEmpty()
        val backdrop = tmdb?.backdropUrl
        val year = tmdb?.year ?: TitleCleaner.yearFromDate(vi?.info?.releasedate.orEmpty())
        val rating = tmdb?.rating?.takeIf { it > 0 }
            ?: vi?.info?.rating?.toDoubleOrNull()?.takeIf { it > 0 }
        val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: vi?.info?.plot.orEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (backdrop != null) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                )
                            ),
                    )
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AsyncImage(
                    model = poster.ifBlank { null },
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    val meta = buildList {
                        year?.let { add(it.toString()) }
                        rating?.let { add("★ ${"%.1f".format(it)}") }
                    }.joinToString("  •  ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (vi?.info?.genre?.isNotBlank() == true) {
                        Spacer(Modifier.height(4.dp))
                        Text(vi.info.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { play() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Ver ahora")
                }
                val vi0 = vi
                if (vi0 != null) {
                    DownloadButton(
                        id = "vod:${vi0.movieData.streamId}",
                        url = repo.vodUrl(vi0.movieData.streamId, vi0.movieData.containerExtension),
                        title = vi0.movieData.name,
                        imageUrl = poster,
                        adaptive = vi0.movieData.containerExtension.lowercase() in listOf("m3u8", "mpd"),
                    )
                }
                if (tmdb?.trailerKey != null) {
                    OutlinedButton(onClick = { openTrailer() }) {
                        Icon(Icons.Default.SmartDisplay, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Trailer")
                    }
                }
                IconButton(onClick = { toggleFavorite() }) {
                    Icon(
                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (overview.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Sinopsis", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))
                Text(overview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val cast = tmdb?.cast.orEmpty()
            if (cast.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Reparto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { CastRow(cast, onActorClick = onActor) }
            }
            val companies = tmdb?.companies.orEmpty()
            if (companies.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Productoras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(companies, key = { it.id }) { c ->
                        CompanyChip(
                            logoUrl = TmdbClient.posterUrl(c.logoPath),
                            name = c.name,
                            onClick = { if (c.id != 0) onCompany(c.id) },
                        )
                    }
                }
            }
            val similar = tmdb?.similar.orEmpty()
            if (similar.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Similares", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(similar, key = { "${it.id}:${it.title.ifBlank { it.name }}" }) { s ->
                        PosterCard(
                            imageUrl = TmdbClient.posterUrl(s.posterPath),
                            title = s.title.ifBlank { s.name },
                            onClick = { openSimilar(s.id, s.title.ifBlank { s.name }) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (loading && info != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(Modifier.padding(16.dp))
        }
    }
}
