package com.mtv.iptv.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvGold
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvRed
import com.mtv.iptv.ui.common.MtvSurface
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.ScreenTopBar
import com.mtv.iptv.ui.mobile.ErrorBox
import com.mtv.iptv.ui.mobile.LoadingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rutas que expone esta pantalla para que el coordinador las registre. */
object LiveTvRoutes {
    const val LIVE_TV = "v12_live_tv"
}

private data class LiveCategoryGroup(
    val id: String,
    val name: String,
    val channels: List<XtreamLiveStream>,
)

/**
 * TV en vivo estilo iMPlayer: tarjetas de categoría expandibles
 * (nombre + conteo de canales + chevron, expansión animada). Al expandir se
 * listan los canales con logo (Coil) y nombre; tocar un canal abre el
 * reproductor existente (PlayerActivity).
 *
 * Los canales se descargan una sola vez (getLiveStreams sin filtro) y se
 * agrupan por categoría fuera del hilo UI.
 *
 * Nota: el data layer no expone EPG (XtreamApi no tiene get_short_epg), así
 * que las filas muestran logo + nombre sin guía de programación.
 */
@Composable
fun LiveTvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var groups by remember { mutableStateOf<List<LiveCategoryGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        try {
            val cats = repo.getLiveCategories().distinctBy { it.categoryId }
            groups = withContext(Dispatchers.Default) {
                val byCat = repo.getLiveStreams(null).groupBy { it.categoryId }
                cats.map { cat ->
                    LiveCategoryGroup(
                        id = cat.categoryId,
                        name = cat.categoryName.ifBlank { "Sin categoría" },
                        channels = byCat[cat.categoryId].orEmpty()
                            .sortedBy { it.name.lowercase() },
                    )
                }.filter { it.channels.isNotEmpty() }
            }
        } catch (e: Exception) {
            error = "No se pudo cargar la TV en vivo."
        }
        loading = false
    }

    fun playChannel(s: XtreamLiveStream) {
        PlayerActivity.start(
            context,
            repo.liveUrl(s.streamId),
            s.name,
            "live:${s.streamId}",
            s.streamIcon,
        )
    }

    MtvUiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MtvBg),
        ) {
            ScreenTopBar(title = "TV en vivo", onBack = onBack)
            when {
                loading -> LoadingBox(Modifier.weight(1f))
                error != null -> ErrorBox(
                    message = error!!,
                    onRetry = { reloadTick++ },
                    modifier = Modifier.weight(1f),
                )
                groups.isEmpty() -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No hay canales disponibles",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MtvOnVariant,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        ExpandableCategoryCard(
                            group = group,
                            expanded = expandedId == group.id,
                            onToggle = {
                                expandedId = if (expandedId == group.id) null else group.id
                            },
                            onChannel = ::playChannel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableCategoryCard(
    group: LiveCategoryGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChannel: (XtreamLiveStream) -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MtvSurface,
            contentColor = MtvOnBg,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MtvRed, Color(0xFF8E1010)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MtvOnBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${group.channels.size} canales",
                        style = MaterialTheme.typography.bodySmall,
                        color = MtvOnVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = MtvGold,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(group.channels, key = { "${it.streamId}:${it.name}" }) { s ->
                        ChannelRow(channel = s, onClick = { onChannel(s) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: XtreamLiveStream, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MtvSurfaceVariant,
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
                model = channel.streamIcon.ifBlank { null },
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MtvBg)
                    .padding(4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MtvOnBg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // EPG: el data layer no expone get_short_epg (ver reporte),
            // así que no se muestra guía de programación.
        }
    }
}
