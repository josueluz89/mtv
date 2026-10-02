package com.mtv.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import kotlinx.coroutines.launch

/**
 * "Mi lista" en TV: grilla de 6 pósters por fila con los favoritos.
 * El click depende del tipo: vod/series abren el detalle, "live" reproduce
 * el canal en vivo y "livegroup" vuelve a la guía de TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFavoritesScreen(
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
    onLiveGroup: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository
    val scope = rememberCoroutineScope()

    val all by container.favoritesRepository.observeAll().collectAsState(initial = emptyList())
    // Los favoritos se guardan por servidor: se muestran los de la sesión
    // actual (sin sesión, todos, como en el móvil).
    val serverId = repo.session?.server?.id
    val favs = remember(all, serverId) {
        if (serverId == null) all else all.filter { it.serverId == serverId }
    }

    fun openLive(favName: String, refId: String, imageUrl: String) {
        val id = refId.toIntOrNull() ?: return
        scope.launch {
            try {
                ExternalPlayer.play(context, container, repo.liveUrl(id), favName, "live:$id", imageUrl)
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MtvBg),
    ) {
        Text(
            "Mi lista",
            style = MaterialTheme.typography.displaySmall,
            color = MtvOnBg,
            modifier = Modifier.padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 8.dp),
        )
        if (favs.isEmpty()) {
            Text(
                "Todavía no agregaste nada a tu lista.",
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
                items(favs, key = { "${it.kind}:${it.refId}" }) { f ->
                    TvPosterCard(
                        title = f.name,
                        imageUrl = f.imageUrl.ifBlank { null },
                        rating = null,
                        onClick = {
                            when (f.kind) {
                                "vod" -> f.refId.toIntOrNull()?.let(onVod)
                                "series" -> f.refId.toIntOrNull()?.let(onSeries)
                                "live" -> openLive(f.name, f.refId, f.imageUrl)
                                "livegroup" -> onLiveGroup()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
