package com.mtv.iptv.ui.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.mtv.iptv.data.remote.xtream.SeriesInfoResponse
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onBack: () -> Unit,
    onActor: (Int) -> Unit,
    onCompany: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository
    val session = repo.session

    var info by remember { mutableStateOf<SeriesInfoResponse?>(null) }
    var tmdb by remember { mutableStateOf<TmdbMedia?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var seasonMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        loading = true
        try {
            val si = repo.getSeriesInfo(seriesId)
            info = si
            tmdb = container.tmdbRepository.findSeries(si.info.name)
            selectedSeason = si.episodes.keys.sortedBy { it.toIntOrNull() ?: 0 }.firstOrNull()
            if (session != null) {
                isFav = container.favoritesRepository.isFavorite(session.server.id, "series", seriesId.toString())
            }
        } catch (e: Exception) {
        }
        loading = false
    }

    fun playEpisode(episodeId: String, ext: String, epTitle: String, image: String) {
        val seriesName = tmdb?.title ?: info?.info?.name.orEmpty()
        PlayerActivity.start(
            context,
            repo.episodeUrl(episodeId, ext),
            "$seriesName — $epTitle",
            "ep:$episodeId",
            image,
        )
    }

    fun toggleFavorite() {
        val si = info ?: return
        val s = session ?: return
        scope.launch {
            isFav = container.favoritesRepository.toggle(
                FavoriteEntity(
                    serverId = s.server.id,
                    kind = "series",
                    refId = seriesId.toString(),
                    name = si.info.name,
                    imageUrl = tmdb?.posterUrl ?: si.info.cover,
                )
            )
        }
    }

    Scaffold { padding ->
        if (loading && info == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val si = info
        val title = tmdb?.title ?: si?.info?.name.orEmpty()
        val poster = tmdb?.posterUrl ?: si?.info?.cover.orEmpty()
        val backdrop = tmdb?.backdropUrl
        val year = tmdb?.year ?: TitleCleaner.yearFromDate(si?.info?.releaseDate.orEmpty())
        val rating = tmdb?.rating?.takeIf { it > 0 }
            ?: si?.info?.rating?.toDoubleOrNull()?.takeIf { it > 0 }
        val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: si?.info?.plot.orEmpty()
        val seasons = si?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 }.orEmpty()
        val episodes = selectedSeason?.let { si?.episodes?.get(it).orEmpty() }?.sortedBy { it.episodeNum }.orEmpty()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
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
                    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            }
            item {
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
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tmdb?.trailerKey != null) {
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${tmdb!!.trailerKey}"))
                                    )
                                }) {
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
                    }
                }
            }
            if (overview.isNotBlank()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Sinopsis", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(overview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            val cast = tmdb?.cast.orEmpty()
            if (cast.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Reparto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) { CastRow(cast, onActorClick = onActor) }
                }
            }
            val companies = tmdb?.companies.orEmpty()
            if (companies.isNotEmpty()) {
                item {
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
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (seasons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Episodios", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Box {
                            Text(
                                "Temporada ${selectedSeason ?: ""} ▾",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { seasonMenuOpen = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            DropdownMenu(
                                expanded = seasonMenuOpen,
                                onDismissRequest = { seasonMenuOpen = false },
                            ) {
                                seasons.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("Temporada $s") },
                                        onClick = { selectedSeason = s; seasonMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(episodes, key = { "${it.id.ifBlank { "ep" }}:${it.episodeNum}:${it.title}" }) { ep ->
                    Card(
                        onClick = {
                            playEpisode(
                                ep.id,
                                ep.containerExtension,
                                ep.title.ifBlank { "Episodio ${ep.episodeNum}" },
                                ep.info.movieImage.ifBlank { poster },
                            )
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = ep.info.movieImage.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(96.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.background),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val epTitle = ep.title.ifBlank { "Episodio ${ep.episodeNum}" }
                                Text(
                                    "${ep.episodeNum}. $epTitle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (ep.info.plot.isNotBlank()) {
                                    Text(
                                        ep.info.plot,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            DownloadButton(
                                id = "ep:${ep.id}",
                                url = repo.episodeUrl(ep.id, ep.containerExtension),
                                title = "$title — ${ep.title.ifBlank { "Episodio ${ep.episodeNum}" }}",
                                imageUrl = ep.info.movieImage.ifBlank { poster },
                                adaptive = ep.containerExtension.lowercase() in listOf("m3u8", "mpd"),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
