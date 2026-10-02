package com.mtv.iptv.ui.detail

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.SeriesInfoResponse
import com.mtv.iptv.data.remote.xtream.XtreamEpisode
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvGold
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvRed
import com.mtv.iptv.ui.common.MtvSurface
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.PrimaryButton
import com.mtv.iptv.ui.common.RowTitle
import com.mtv.iptv.ui.common.SecondaryButton
import com.mtv.iptv.ui.common.buildMeta
import com.mtv.iptv.ui.common.openYoutube
import com.mtv.iptv.ui.common.parseRating
import com.mtv.iptv.ui.mobile.CastRow
import com.mtv.iptv.ui.mobile.CompanyChip
import com.mtv.iptv.ui.downloads.DownloadButton
import com.mtv.iptv.ui.mobile.LoadingBox
import kotlinx.coroutines.launch

/**
 * Detalle de serie estilo iMPlayer: backdrop grande con degradado, póster,
 * título + año, metadata (géneros/rating/episodios), botón VER AHORA
 * prominente (reproduce el primer episodio), toggle de favorito, trailer,
 * sinopsis, reparto tocable (→ ActorDetailScreen existente de v1.1),
 * productoras (→ CompanyDetailScreen existente de v1.1), tabs de temporadas,
 * lista de episodios (con descarga v1.1) y sección de trailers (TMDB).
 *
 * Reutiliza del data layer: XtreamRepository.getSeriesInfo / episodeUrl,
 * TmdbRepository.findSeries (con caché), FavoritesRepository.
 * Reutiliza de ui.mobile: CastRow, CompanyChip, DownloadButton, LoadingBox.
 */
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

    LaunchedEffect(seriesId) {
        loading = true
        try {
            val si = repo.getSeriesInfo(seriesId)
            info = si
            tmdb = container.tmdbRepository.findSeries(si.info.name)
            selectedSeason = si.episodes.keys.sortedBy { it.toIntOrNull() ?: 0 }.firstOrNull()
            if (session != null) {
                isFav = container.favoritesRepository.isFavorite(
                    session.server.id, "series", seriesId.toString(),
                )
            }
        } catch (e: Exception) {
            // se muestra lo que haya
        }
        loading = false
    }

    fun playEpisode(ep: XtreamEpisode, seriesName: String, poster: String, season: String?) {
        val epTitle = ep.title.ifBlank { "Episodio ${ep.episodeNum}" }
        ExternalPlayer.play(context, container,
            repo.episodeUrl(ep.id, ep.containerExtension),
            "$seriesName — $epTitle",
            "ep:${ep.id}",
            ep.info.movieImage.ifBlank { poster },
            subTmdbId = tmdb?.tmdbId,
            subSeason = season?.toIntOrNull(),
            subEpisode = ep.episodeNum,
        )
    }

    fun playFirst() {
        val si = info ?: return
        val firstSeason = si.episodes.keys.sortedBy { it.toIntOrNull() ?: 0 }.firstOrNull()
            ?: return
        val firstEp = si.episodes[firstSeason].orEmpty()
            .sortedBy { it.episodeNum }.firstOrNull() ?: return
        val poster = tmdb?.posterUrl ?: si.info.cover
        playEpisode(firstEp, tmdb?.title ?: si.info.name, poster, firstSeason)
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
                ),
            )
        }
    }

    MtvUiTheme {
        Scaffold(containerColor = MtvBg) { padding ->
            if (loading && info == null) {
                LoadingBox(Modifier.padding(padding))
                return@Scaffold
            }
            val si = info
            val title = tmdb?.title ?: si?.info?.name.orEmpty()
            val poster = tmdb?.posterUrl ?: si?.info?.cover.orEmpty()
            val backdrop = tmdb?.backdropUrl
            val year = tmdb?.year ?: TitleCleaner.yearFromDate(si?.info?.releaseDate.orEmpty())
            val rating = tmdb?.rating?.takeIf { it > 0 } ?: parseRating(si?.info?.rating.orEmpty())
            val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: si?.info?.plot.orEmpty()
            val genres = si?.info?.genre?.takeIf { it.isNotBlank() }
            val totalEps = si?.episodes?.values?.sumOf { it.size } ?: 0
            val seasons = si?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 }.orEmpty()
            val episodes = selectedSeason?.let { si?.episodes?.get(it).orEmpty() }
                ?.sortedBy { it.episodeNum }.orEmpty()
            val trailerKey = tmdb?.trailerKey

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Backdrop grande con degradado + botón atrás.
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
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(MtvSurface, MtvBg),
                                        ),
                                    ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent,
                                            MtvBg,
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                        ) {
                            SecondaryButton(
                                text = "Atrás",
                                onClick = onBack,
                                icon = Icons.Default.ArrowBack,
                            )
                        }
                    }
                }
                // Póster + título + metadata.
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(MtvSurfaceVariant),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall, color = MtvOnBg)
                            Spacer(Modifier.height(6.dp))
                            val meta = buildMeta(
                                year,
                                rating,
                                buildList {
                                    if (seasons.isNotEmpty()) {
                                        add(
                                            "${seasons.size} temporada" +
                                                if (seasons.size == 1) "" else "s",
                                        )
                                    }
                                    if (totalEps > 0) add("$totalEps episodios")
                                }.joinToString(" • ").ifBlank { null },
                            )
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MtvGold,
                                )
                            }
                            if (genres != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    genres,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MtvOnVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                // VER AHORA prominente (primer episodio).
                item {
                    PrimaryButton(
                        text = "VER AHORA",
                        onClick = { playFirst() },
                        icon = Icons.Default.PlayArrow,
                        enabled = totalEps > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                    )
                }
                // Trailer + favorito.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (trailerKey != null) {
                            SecondaryButton(
                                text = "Trailer",
                                onClick = { openYoutube(context, trailerKey) },
                                icon = Icons.Default.SmartDisplay,
                            )
                        }
                        SecondaryButton(
                            text = if (isFav) "En favoritos" else "Favorito",
                            onClick = { toggleFavorite() },
                            icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        )
                    }
                }
                if (overview.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Sinopsis",
                            style = MaterialTheme.typography.titleMedium,
                            color = MtvOnBg,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MtvOnBg,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                val cast = tmdb?.cast.orEmpty()
                if (cast.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Reparto",
                            style = MaterialTheme.typography.titleMedium,
                            color = MtvOnBg,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            CastRow(cast, onActorClick = onActor)
                        }
                    }
                }
                val companies = tmdb?.companies.orEmpty()
                if (companies.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Productoras",
                            style = MaterialTheme.typography.titleMedium,
                            color = MtvOnBg,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
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
                }
                // Tabs de temporadas.
                if (seasons.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        RowTitle("Temporadas")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons, key = { it }) { s ->
                                val selected = s == selectedSeason
                                Button(
                                    onClick = { selectedSeason = s },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) MtvRed else MtvSurfaceVariant,
                                        contentColor = if (selected) Color.White else MtvOnBg,
                                    ),
                                ) {
                                    Text("T$s")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    items(
                        episodes,
                        key = { "${it.id.ifBlank { "ep" }}:${it.episodeNum}:${it.title}" },
                    ) { ep ->
                        EpisodeRow(
                            episode = ep,
                            poster = poster,
                            onPlay = { playEpisode(ep, title, poster, selectedSeason) },
                        )
                    }
                }
                // Sección de trailers (TMDB).
                if (trailerKey != null) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        RowTitle("Trailers")
                    }
                    item {
                        Card(
                            onClick = { openYoutube(context, trailerKey) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MtvSurface,
                                contentColor = MtvOnBg,
                            ),
                        ) {
                            Box {
                                AsyncImage(
                                    model = backdrop ?: poster.ifBlank { null },
                                    contentDescription = "Trailer",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MtvSurfaceVariant),
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.7f),
                                                ),
                                            ),
                                            RoundedCornerShape(16.dp),
                                        ),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MtvRed.copy(alpha = 0.92f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                Text(
                                    "Trailer oficial",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: XtreamEpisode,
    poster: String,
    onPlay: () -> Unit,
) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository
    val epTitle = episode.title.ifBlank { "Episodio ${episode.episodeNum}" }

    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MtvSurface,
            contentColor = MtvOnBg,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = episode.info.movieImage.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MtvSurfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${episode.episodeNum}. $epTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MtvOnBg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.info.plot.isNotBlank()) {
                    Text(
                        episode.info.plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = MtvOnVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DownloadButton(
                id = "ep:${episode.id}",
                url = repo.episodeUrl(episode.id, episode.containerExtension),
                title = epTitle,
                imageUrl = episode.info.movieImage.ifBlank { poster },
                adaptive = episode.containerExtension.lowercase() in listOf("m3u8", "mpd"),
            )
        }
    }
}
