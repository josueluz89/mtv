package com.mtv.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvRed
import com.mtv.iptv.ui.common.MtvSurfaceVariant

/**
 * Historial en TV: últimos 60 registros de reproducción en tarjetas con
 * barra de progreso. El click continúa directamente (PlayerActivity reanuda
 * la posición guardada por mediaKey).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHistoryScreen() {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    var items by remember { mutableStateOf<List<PlaybackEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        items = try {
            container.playbackRepository.recent(60)
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MtvBg)) {
        Text(
            "Historial",
            style = MaterialTheme.typography.displaySmall,
            color = MtvOnBg,
            modifier = Modifier.padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 8.dp),
        )
        if (items.isEmpty()) {
            Text(
                "Sin actividad reciente.",
                style = MaterialTheme.typography.headlineSmall,
                color = MtvOnVariant,
                modifier = Modifier.padding(32.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(32.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(items, key = { it.mediaKey }) { e ->
                    TvHistoryCard(
                        entity = e,
                        onClick = {
                            if (e.url.isNotBlank()) {
                                ExternalPlayer.play(context, container, e.url, e.name, e.mediaKey, e.imageUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvHistoryCard(
    entity: PlaybackEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frac = if (entity.durationMs > 0) {
        (entity.positionMs.toFloat() / entity.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Card(onClick = onClick, modifier = modifier) {
        Column {
            Box {
                AsyncImage(
                    model = entity.imageUrl.ifBlank { null },
                    contentDescription = entity.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MtvSurfaceVariant),
                )
                // Barra de progreso sobre la parte baja del thumbnail.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .background(MtvRed),
                    )
                }
            }
            Text(
                entity.name.ifBlank { entity.mediaKey },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MtvOnBg,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
