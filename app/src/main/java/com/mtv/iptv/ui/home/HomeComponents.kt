package com.mtv.iptv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbSearchResult
import com.mtv.iptv.ui.mobile.safeClickable
import kotlinx.coroutines.delay

/** Convierte el rating Xtream ("7.5", "") a Double?. */
fun parseRating(raw: String): Double? = raw.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

// ---------------- Carrusel hero (tendencias TMDB) ----------------

/**
 * Hero a todo ancho estilo iMPlayer: backdrop con degradado, título,
 * metadatos, puntitos de página y avance automático cada 6 s.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    items: List<TmdbSearchResult>,
    resolving: Boolean,
    onItemClick: (TmdbSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }

    Box(modifier = modifier
        .fillMaxWidth()
        .height(380.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            val title = item.title.ifBlank { item.name }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeClickable { onItemClick(item) },
            ) {
                AsyncImage(
                    model = TmdbClient.backdropUrl(item.backdropPath),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Degradado: funde el backdrop con el fondo del tema.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.voteAverage > 0) {
                            Text(
                                "★ ${"%.1f".format(item.voteAverage)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFF0B429),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (item.mediaType == "tv") "Serie" else "Película",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    if (item.overview.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Puntitos de página.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .size(if (i == pagerState.currentPage) 22.dp else 7.dp, 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pagerState.currentPage) Color(0xFFE02020)
                            else Color.White.copy(alpha = 0.45f),
                        ),
                )
            }
        }
        if (resolving) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFE02020),
            )
        }
    }
}

// ---------------- Tarjetas ----------------

/** Póster 2:3 con elevación y badge de rating (★). */
@Composable
fun RatingPosterCard(
    imageUrl: String?,
    title: String,
    rating: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(128.dp)
            .safeClickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (rating != null && rating > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "★ ${"%.1f".format(rating)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF0B429),
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** "Seguir viendo" 16:9 con elevación y barra de progreso. */
@Composable
fun ContinueWatchingHomeCard(
    imageUrl: String?,
    name: String,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(220.dp)
            .safeClickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            LinearProgressIndicator(
                progress = progress,
                color = Color(0xFFE02020),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Logo de canal en vivo con elevación (fondo claro para logos con transparencia). */
@Composable
fun ChannelLogoCard(
    iconUrl: String?,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(140.dp)
            .safeClickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F0EB)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
