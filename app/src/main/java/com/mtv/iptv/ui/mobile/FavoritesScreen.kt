package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    kindFilter: String? = null,
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository
    val favorites by container.favoritesRepository.observeAll().collectAsState(initial = emptyList())
    val shown = if (kindFilter == null) favorites else favorites.filter { it.kind == kindFilter }

    fun open(fav: FavoriteEntity) {
        when (fav.kind) {
            "live" -> fav.refId.toIntOrNull()?.let {
                ExternalPlayer.play(context, container, repo.liveUrl(it), fav.name, "live:$it", fav.imageUrl)
            }
            "vod" -> fav.refId.toIntOrNull()?.let { onVod(it) }
            "series" -> fav.refId.toIntOrNull()?.let { onSeries(it) }
        }
    }

    fun kindLabel(kind: String) = when (kind) {
        "live" -> "En vivo"
        "vod" -> "Película"
        "series" -> "Serie"
        else -> kind
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (kindFilter == null) "Favoritos"
                        else "Favoritos · ${kindLabel(kindFilter)}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        if (shown.isEmpty()) {
            ErrorBox(
                message = "Todavía no tenés favoritos.",
                onRetry = onBack,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { "${it.serverId}-${it.kind}-${it.refId}" }) { fav ->
                Card(
                    onClick = { open(fav) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = fav.imageUrl.ifBlank { null },
                            contentDescription = fav.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fav.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                kindLabel(fav.kind),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch { container.favoritesRepository.delete(fav) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Quitar de favoritos")
                        }
                    }
                }
            }
        }
    }
}
