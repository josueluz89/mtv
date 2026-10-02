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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.MaterialTheme
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
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.VodInfoResponse
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.ImPosterCard
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvGold
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
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

/** Rutas que exponen las pantallas de detalle para que el coordinador las registre. */
object DetailRoutes {
    const val MOVIE = "v12_movie/{streamId}"
    const val SERIE = "v12_serie/{seriesId}"

    fun movie(streamId: Int) = "v12_movie/$streamId"
    fun serie(seriesId: Int) = "v12_serie/$seriesId"
}

/**
 * Detalle de película estilo iMPlayer: backdrop grande con degradado, póster,
 * título + año, metadata (duración/géneros/rating), botón VER AHORA prominente,
 * toggle de favorito, descarga (v1.1), trailer, sinopsis, reparto tocable
 * (→ ActorDetailScreen existente de v1.1) y productoras (→ CompanyDetailScreen
 * existente de v1.1).
 *
 * Reutiliza del data layer: XtreamRepository.getVodInfo / vodUrl /
 * findVodByTitle, TmdbRepository.findMovie (con caché), FavoritesRepository.
 * Reutiliza de ui.mobile: CastRow, CompanyChip, DownloadButton, LoadingBox.
 */
@Composable
fun MovieDetailScreen(
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
                isFav = container.favoritesRepository.isFavorite(
                    session.server.id, "vod", streamId.toString(),
                )
            }
        } catch (e: Exception) {
            // se muestra lo que haya
        }
        loading = false
    }

    fun play() {
        val vi = info ?: return
        ExternalPlayer.play(context, container,
            repo.vodUrl(vi.movieData.streamId, vi.movieData.containerExtension),
            vi.movieData.name,
            "vod:${vi.movieData.streamId}",
            tmdb?.posterUrl ?: vi.info.movieImage,
            subTmdbId = tmdb?.tmdbId,
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
                ),
            )
        }
    }

    fun openSimilar(title: String) {
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

    MtvUiTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MtvBg,
        ) { padding ->
            if (loading && info == null) {
                LoadingBox(Modifier.padding(padding))
                return@Scaffold
            }
            val vi = info
            val title = tmdb?.title ?: vi?.movieData?.name.orEmpty()
            val poster = tmdb?.posterUrl ?: vi?.info?.movieImage.orEmpty()
            val backdrop = tmdb?.backdropUrl
            val year = tmdb?.year ?: TitleCleaner.yearFromDate(vi?.info?.releasedate.orEmpty())
            val rating = tmdb?.rating?.takeIf { it > 0 } ?: parseRating(vi?.info?.rating.orEmpty())
            val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: vi?.info?.plot.orEmpty()
            val duration = vi?.info?.duration?.takeIf { it.isNotBlank() }
            val genres = vi?.info?.genre?.takeIf { it.isNotBlank() }
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
                            val meta = buildMeta(year, rating, duration)
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
                // VER AHORA prominente.
                item {
                    PrimaryButton(
                        text = "VER AHORA",
                        onClick = { play() },
                        icon = Icons.Default.PlayArrow,
                        enabled = vi != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                    )
                }
                // Descarga + trailer + favorito.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val vi0 = vi
                        if (vi0 != null) {
                            DownloadButton(
                                id = "vod:${vi0.movieData.streamId}",
                                url = repo.vodUrl(
                                    vi0.movieData.streamId,
                                    vi0.movieData.containerExtension,
                                ),
                                title = vi0.movieData.name,
                                imageUrl = poster,
                                adaptive = vi0.movieData.containerExtension.lowercase() in
                                    listOf("m3u8", "mpd"),
                            )
                        }
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
                val similar = tmdb?.similar.orEmpty()
                if (similar.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        RowTitle("Similares")
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                similar,
                                key = { "${it.id}:${it.title.ifBlank { it.name }}" },
                            ) { s ->
                                ImPosterCard(
                                    imageUrl = TmdbClient.posterUrl(s.posterPath),
                                    title = s.title.ifBlank { s.name },
                                    rating = s.voteAverage.takeIf { it > 0 },
                                    onClick = { openSimilar(s.title.ifBlank { s.name }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
