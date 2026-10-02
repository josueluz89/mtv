package com.mtv.iptv.ui.tv

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.data.remote.xtream.SeriesInfoResponse
import com.mtv.iptv.data.remote.xtream.VodInfoResponse
import com.mtv.iptv.data.remote.xtream.XtreamCategory
import com.mtv.iptv.data.remote.xtream.XtreamEpisode
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.ExternalPlayer
import com.mtv.iptv.player.downloads.DownloadEntry
import com.mtv.iptv.player.downloads.EstadoDescarga
import com.mtv.iptv.ui.common.MtvBg
import com.mtv.iptv.ui.common.MtvGold
import com.mtv.iptv.ui.common.MtvOnBg
import com.mtv.iptv.ui.common.MtvOnVariant
import com.mtv.iptv.ui.common.MtvRed
import com.mtv.iptv.ui.common.MtvSurface
import com.mtv.iptv.ui.common.MtvSurfaceVariant
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.common.openYoutube
import com.mtv.iptv.ui.mobile.LoginFlowState
import com.mtv.iptv.ui.mobile.SettingsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Azul de los badges de rating, como en la referencia TiviMate. */
private val TvRatingBlue = Color(0xFF1E88E5)

// ---------------- App + navegación ----------------

/**
 * UI de TV estilo TiviMate: riel delgado de iconos a la izquierda,
 * segunda columna de navegación (grupos/carpetas) y área de contenido.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp() {
    MtvUiTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "tv_servers") {
            composable("tv_servers") {
                TvServersScreen(
                    onConnected = {
                        navController.navigate("tv_main/tv") {
                            popUpTo("tv_servers") { inclusive = true }
                        }
                    },
                )
            }
            composable(
                "tv_main/{section}",
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { entry ->
                TvMainScreen(
                    section = entry.arguments?.getString("section") ?: "tv",
                    vodId = null,
                    seriesId = null,
                    navController = navController,
                )
            }
            composable(
                "tv_main/movies/vod/{streamId}",
                arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
            ) { entry ->
                TvMainScreen(
                    section = "movies",
                    vodId = entry.arguments?.getInt("streamId"),
                    seriesId = null,
                    navController = navController,
                )
            }
            composable(
                "tv_main/series/series/{seriesId}",
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
            ) { entry ->
                TvMainScreen(
                    section = "series",
                    vodId = null,
                    seriesId = entry.arguments?.getInt("seriesId"),
                    navController = navController,
                )
            }
            composable("tv_add_user") {
                TvServersScreen(
                    addMode = true,
                    onConnected = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("tv_speedtest") {
                com.mtv.iptv.ui.speedtest.SpeedTestScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private data class TvRailItem(
    val section: String,
    val label: String,
    val icon: ImageVector,
)

private val tvRailItems = listOf(
    TvRailItem("search", "Buscar", Icons.Default.Search),
    TvRailItem("tv", "TV", Icons.Default.Tv),
    TvRailItem("movies", "Películas", Icons.Default.Movie),
    TvRailItem("series", "Shows", Icons.Default.LiveTv),
    TvRailItem("downloads", "Descargas", Icons.Default.Download),
    TvRailItem("settings", "Ajustes", Icons.Default.Settings),
)

// ---------------- Shell: riel + columna + contenido ----------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(
    section: String,
    vodId: Int?,
    seriesId: Int?,
    navController: NavController,
) {
    Row(Modifier.fillMaxSize().background(MtvBg)) {
        TvIconRail(
            selectedSection = section,
            onSelect = { s ->
                if (s != section) {
                    navController.navigate("tv_main/$s") {
                        popUpTo("tv_main/$section") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            },
        )
        when (section) {
            "tv" -> TvLiveMain()
            "movies" -> TvMoviesMain(
                vodId = vodId,
                onVod = { navController.navigate("tv_main/movies/vod/$it") },
            )
            "series" -> TvSeriesMain(
                seriesId = seriesId,
                onSeries = { navController.navigate("tv_main/series/series/$it") },
            )
            "search" -> Box(Modifier.weight(1f).fillMaxHeight()) {
                TvSearchScreen(
                    onBack = {},
                    onVod = { navController.navigate("tv_main/movies/vod/$it") },
                    onSeries = { navController.navigate("tv_main/series/series/$it") },
                )
            }
            "downloads" -> Box(Modifier.weight(1f).fillMaxHeight()) {
                TvDownloadsScreen(onBack = {})
            }
            "settings" -> Box(Modifier.weight(1f).fillMaxHeight()) {
                TvSettingsScreen(
                    onBack = {},
                    onServers = {
                        navController.navigate("tv_servers") {
                            popUpTo("tv_main/$section") { inclusive = true }
                        }
                    },
                    onLogout = {
                        LoginFlowState.skipAutoLoginOnce = true
                        navController.navigate("tv_servers") {
                            popUpTo("tv_main/$section") { inclusive = true }
                        }
                    },
                    onHome = {
                        navController.navigate("tv_main/tv") {
                            popUpTo("tv_main/$section") { inclusive = true }
                        }
                    },
                    onAddUser = { navController.navigate("tv_add_user") },
                    onSpeedTest = { navController.navigate("tv_speedtest") },
                )
            }
        }
    }
}

/**
 * Riel delgado de iconos (extrema izquierda). Se expande mostrando etiquetas
 * cuando recibe el foco, como en la referencia.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvIconRail(
    selectedSection: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) 230.dp else 78.dp,
    )
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MtvSurface)
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "MTV",
            style = MaterialTheme.typography.headlineSmall,
            color = MtvRed,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        tvRailItems.forEach { item ->
            TvRailRow(
                selected = item.section == selectedSection,
                expanded = expanded,
                item = item,
                onClick = { onSelect(item.section) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRailRow(
    selected: Boolean,
    expanded: Boolean,
    item: TvRailItem,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(
                when {
                    selected -> MtvRed
                    focused -> MtvSurfaceVariant
                    else -> Color.Transparent
                },
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (selected || focused) Color.White else MtvOnVariant,
            modifier = Modifier.size(24.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Text(
                item.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) Color.White else MtvOnBg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------- Columna de navegación (grupos / carpetas) ----------------

/**
 * Segunda columna: lista de grupos (TV) o carpetas (Películas/Series),
 * con conteo. La primera entrada puede ser "todo".
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavColumn(
    entries: List<TvNavEntry>,
    selectedId: String?,
    onSelect: (TvNavEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(MtvBg)
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            TvNavRow(
                entry = entry,
                selected = entry.id == selectedId,
                onClick = { onSelect(entry) },
            )
        }
    }
}

data class TvNavEntry(
    val id: String,
    val label: String,
    val count: Int? = null,
    val isFolder: Boolean = false,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvNavRow(
    entry: TvNavEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .background(
                when {
                    selected -> MtvRed
                    focused -> MtvSurfaceVariant
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.isFolder) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MtvGold,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            entry.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Color.White else MtvOnBg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "${entry.count}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Color.White else MtvOnVariant,
            )
        }
    }
}

// ---------------- Sección TV: lista de canales + grilla EPG ----------------

/**
 * Sección TV estilo TiviMate: grupos a la izquierda, lista de canales y
 * grilla de programación con franjas horarias. Sin datos de EPG (decisión
 * del dueño), las celdas muestran "Sin información".
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLiveMain() {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var groups by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<XtreamCategory?>(null) }
    var channels by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            groups = repo.getLiveCategories().distinctBy { it.categoryId }
            selectedGroup = groups.firstOrNull()
        } catch (e: Exception) {
        }
    }
    LaunchedEffect(selectedGroup) {
        try {
            channels = repo.getLiveStreams(selectedGroup?.categoryId)
        } catch (e: Exception) {
            channels = emptyList()
        }
    }

    Row(Modifier.fillMaxSize()) {
        val entries = remember(groups) {
            groups.map { g ->
                TvNavEntry(id = g.categoryId, label = g.categoryName)
            }
        }
        TvNavColumn(
            entries = entries,
            selectedId = selectedGroup?.categoryId,
            onSelect = { e -> selectedGroup = groups.firstOrNull { it.categoryId == e.id } },
            modifier = Modifier.width(300.dp).fillMaxHeight(),
        )
        TvEpgGuide(
            channels = channels,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Franjas de 30 min: 4 slots desde la hora actual redondeada hacia abajo. */
private fun epgTimeSlots(): List<Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.MINUTE, if (cal.get(Calendar.MINUTE) >= 30) 30 else 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return List(4) { i -> cal.timeInMillis + i * 30L * 60L * 1000L }
}

private fun epgFormatTime(millis: Long?): String {
    if (millis == null) return ""
    return SimpleDateFormat("hh:mm a", Locale("es")).format(java.util.Date(millis))
        .lowercase(Locale("es"))
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvEpgGuide(
    channels: List<XtreamLiveStream>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository
    val scope = rememberCoroutineScope()

    val slots = remember { epgTimeSlots() }
    var focusedChannel by remember { mutableStateOf<XtreamLiveStream?>(null) }
    val shown = focusedChannel ?: channels.firstOrNull()

    Column(modifier.background(MtvBg)) {
        // Panel superior: canal seleccionado + "Sin información".
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.Black, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = shown?.streamIcon?.ifBlank { null },
                    contentDescription = shown?.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(84.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shown?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MtvOnBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sin información",
                    style = MaterialTheme.typography.titleLarge,
                    color = MtvOnBg,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${epgFormatTime(slots.firstOrNull())} – ${epgFormatTime(slots.getOrNull(1))}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MtvOnVariant,
                )
            }
        }

        // Grilla: cabecera de horarios + filas de canales.
        LazyColumn(modifier = Modifier.weight(1f)) {
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MtvBg)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(320.dp))
                    slots.forEach { t ->
                        Text(
                            epgFormatTime(t),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MtvOnVariant,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            items(channels, key = { it.streamId }) { ch ->
                TvEpgRow(
                    channel = ch,
                    onFocus = { focusedChannel = ch },
                    onPlay = {
                        scope.launch {
                            try {
                                ExternalPlayer.play(
                                    context, container,
                                    repo.liveUrl(ch.streamId),
                                    ch.name, "live:${ch.streamId}", ch.streamIcon,
                                )
                            } catch (e: Exception) {
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvEpgRow(
    channel: XtreamLiveStream,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onPlay,
            )
            .background(
                if (focused) MtvSurfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Celda del canal: número, logo, nombre.
        Row(
            modifier = Modifier.width(312.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${channel.num}",
                style = MaterialTheme.typography.titleMedium,
                color = MtvOnVariant,
                modifier = Modifier.width(44.dp).padding(start = 8.dp),
            )
            AsyncImage(
                model = channel.streamIcon.ifBlank { null },
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(46.dp)
                    .background(MtvSurfaceVariant, RoundedCornerShape(6.dp))
                    .padding(4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = MtvOnBg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // Celdas de programación (sin datos de EPG).
        repeat(4) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp)
                    .background(MtvSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(
                    "Sin información",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MtvOnVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------- Secciones Películas / Series ----------------

/**
 * Sección Películas estilo TiviMate: carpetas a la izquierda
 * ("Todas las películas" + categorías con conteo), grilla de pósters
 * con rating a la derecha.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMoviesMain(
    vodId: Int?,
    onVod: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var folders by remember { mutableStateOf<List<Pair<XtreamCategory, Int>>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) } // null = todas
    var items by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val categories = repo.getVodCategories().distinctBy { it.categoryId }
            val all = repo.getVodStreams(null)
            folders = withContext(Dispatchers.Default) {
                val counts = all.groupingBy { it.categoryId }.eachCount()
                categories.mapNotNull { cat ->
                    val total = counts[cat.categoryId] ?: 0
                    if (total > 0) cat to total else null
                }
            }
        } catch (e: Exception) {
        }
    }
    LaunchedEffect(selectedId) {
        try {
            items = repo.getVodStreams(selectedId)
        } catch (e: Exception) {
            items = emptyList()
        }
    }

    if (vodId != null) {
        TvVodDetail(
            streamId = vodId,
            folderName = folders.firstOrNull { it.first.categoryId == selectedId }?.first?.categoryName,
            onVod = onVod,
        )
        return
    }

    Row(Modifier.fillMaxSize()) {
        val entries = remember(folders) {
            listOf(TvNavEntry(id = "__all__", label = "Todas las películas")) +
                folders.map { (cat, total) ->
                    TvNavEntry(id = cat.categoryId, label = cat.categoryName, count = total, isFolder = true)
                }
        }
        TvNavColumn(
            entries = entries,
            selectedId = selectedId ?: "__all__",
            onSelect = { e -> selectedId = e.id.takeIf { it != "__all__" } },
            modifier = Modifier.width(300.dp).fillMaxHeight(),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().background(MtvBg),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.streamId }) { v ->
                TvPosterCard(
                    title = v.name,
                    imageUrl = v.streamIcon.ifBlank { null },
                    rating = v.rating.toDoubleOrNull()?.takeIf { it > 0 },
                    onClick = { onVod(v.streamId) },
                )
            }
        }
    }
}

/**
 * Sección Series estilo TiviMate: carpetas a la izquierda
 * ("Todos los shows" + categorías con conteo), grilla de pósters a la derecha.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSeriesMain(
    seriesId: Int?,
    onSeries: (Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var folders by remember { mutableStateOf<List<Pair<XtreamCategory, Int>>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) } // null = todas
    var items by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val categories = repo.getSeriesCategories().distinctBy { it.categoryId }
            val all = repo.getSeries(null)
            folders = withContext(Dispatchers.Default) {
                val counts = all.groupingBy { it.categoryId }.eachCount()
                categories.mapNotNull { cat ->
                    val total = counts[cat.categoryId] ?: 0
                    if (total > 0) cat to total else null
                }
            }
        } catch (e: Exception) {
        }
    }
    LaunchedEffect(selectedId) {
        try {
            items = repo.getSeries(selectedId)
        } catch (e: Exception) {
            items = emptyList()
        }
    }

    if (seriesId != null) {
        TvSeriesDetail(
            seriesId = seriesId,
            folderName = folders.firstOrNull { it.first.categoryId == selectedId }?.first?.categoryName,
            onSeries = onSeries,
        )
        return
    }

    Row(Modifier.fillMaxSize()) {
        val entries = remember(folders) {
            listOf(TvNavEntry(id = "__all__", label = "Todos los shows")) +
                folders.map { (cat, total) ->
                    TvNavEntry(id = cat.categoryId, label = cat.categoryName, count = total, isFolder = true)
                }
        }
        TvNavColumn(
            entries = entries,
            selectedId = selectedId ?: "__all__",
            onSelect = { e -> selectedId = e.id.takeIf { it != "__all__" } },
            modifier = Modifier.width(300.dp).fillMaxHeight(),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().background(MtvBg),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.seriesId }) { s ->
                TvPosterCard(
                    title = s.name,
                    imageUrl = s.cover.ifBlank { null },
                    rating = s.rating.toDoubleOrNull()?.takeIf { it > 0 },
                    onClick = { onSeries(s.seriesId) },
                )
            }
        }
    }
}

/**
 * Tarjeta de póster con badge de rating (azul, como la referencia)
 * y título debajo.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPosterCard(
    title: String,
    imageUrl: String?,
    rating: Double?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(MtvSurfaceVariant),
                )
                if (rating != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(TvRatingBlue, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            String.format(Locale.US, "%.1f", rating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
            }
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MtvOnBg,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

// ---------------- Detalle película (TV, estilo TiviMate) ----------------

private fun formatDuration(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRatingBadge(rating: Double) {
    if (rating <= 0) return
    Box(
        modifier = Modifier
            .background(TvRatingBlue, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            String.format(Locale.US, "%.1f", rating),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvDetailAction(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(end = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) Color.White else MtvOnBg,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (primary) Color.White else MtvOnBg,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvVodDetail(
    streamId: Int,
    folderName: String?,
    onVod: (Int) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<VodInfoResponse?>(null) }
    var tmdb by remember { mutableStateOf<TmdbMedia?>(null) }
    var isFav by remember { mutableStateOf(false) }
    val session = repo.session

    LaunchedEffect(streamId) {
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
        }
    }

    val vi = info
    val title = tmdb?.title ?: vi?.movieData?.name.orEmpty()
    val backdrop = tmdb?.backdropUrl
    val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: vi?.info?.plot.orEmpty()
    val year = tmdb?.year ?: TitleCleaner.yearFromDate(vi?.info?.releasedate.orEmpty())
    val rating = tmdb?.rating ?: 0.0
    val duration = formatDuration(tmdb?.runtimeMinutes)
    val genres = tmdb?.genres?.joinToString(", ").orEmpty()
    val cast = tmdb?.cast?.take(8)?.joinToString(", ") { it.name }.orEmpty()
    val director = tmdb?.director.orEmpty()
    val trailerKey = tmdb?.trailerKey
    val metaLine = listOfNotNull(
        year?.toString(),
        duration.ifBlank { null },
        genres.ifBlank { null },
    ).joinToString(" · ")

    fun play() {
        val v = vi ?: return
        ExternalPlayer.play(
            context, container,
            repo.vodUrl(v.movieData.streamId, v.movieData.containerExtension),
            v.movieData.name,
            "vod:${v.movieData.streamId}",
            tmdb?.posterUrl ?: v.info.movieImage,
            subTmdbId = tmdb?.tmdbId,
        )
    }

    fun toggleFavorite() {
        val v = vi ?: return
        val s = session ?: return
        scope.launch {
            isFav = container.favoritesRepository.toggle(
                FavoriteEntity(
                    serverId = s.server.id,
                    kind = "vod",
                    refId = streamId.toString(),
                    name = v.movieData.name,
                    imageUrl = tmdb?.posterUrl ?: v.info.movieImage,
                ),
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MtvBg),
    ) {
        // Backdrop con degradado + info encima.
        item {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                AsyncImage(
                    model = backdrop?.ifBlank { null },
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MtvSurfaceVariant),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MtvBg),
                            startY = 200f,
                        ),
                    ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 36.dp, end = 36.dp, bottom = 8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvRatingBadge(rating)
                        if (metaLine.isNotBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Text(metaLine, style = MaterialTheme.typography.titleMedium, color = MtvOnBg)
                        }
                    }
                    if (cast.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text("Reparto ", style = MaterialTheme.typography.bodyLarge, color = MtvOnVariant)
                            Text(
                                cast,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MtvOnBg,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (director.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("Director ", style = MaterialTheme.typography.bodyLarge, color = MtvOnVariant)
                            Text(director, style = MaterialTheme.typography.bodyLarge, color = MtvOnBg)
                        }
                    }
                }
            }
        }
        // Sinopsis.
        if (overview.isNotBlank()) {
            item {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MtvOnBg,
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
                )
            }
        }
        // Chip de carpeta.
        if (!folderName.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MtvGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(folderName, style = MaterialTheme.typography.titleMedium, color = MtvOnBg)
                }
            }
        }
        // Botones de acción.
        item {
            Row(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 16.dp),
            ) {
                TvDetailAction(label = "Reproducir", icon = Icons.Default.PlayArrow, primary = true, onClick = { play() })
                if (activity != null && vi != null) {
                    TvDetailAction(
                        label = "Abrir en reproductor externo",
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        onClick = {
                            ExternalPlayer.playExternal(
                                activity,
                                repo.vodUrl(vi.movieData.streamId, vi.movieData.containerExtension),
                                vi.movieData.name,
                                "vod:${vi.movieData.streamId}",
                                container,
                            )
                        },
                    )
                }
                if (trailerKey != null) {
                    TvDetailAction(
                        label = "Trailer",
                        icon = Icons.Default.Subtitles,
                        onClick = { openYoutube(context, trailerKey) },
                    )
                }
                TvDetailAction(
                    label = if (isFav) "En mi lista" else "Agregar a mi lista",
                    icon = Icons.Default.Folder,
                    onClick = { toggleFavorite() },
                )
            }
        }
        // Relacionadas.
        val similar = tmdb?.similar.orEmpty()
        if (similar.isNotEmpty()) {
            item {
                Text(
                    "Relacionadas",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MtvOnBg,
                    modifier = Modifier.padding(start = 36.dp, top = 8.dp, bottom = 12.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(similar, key = { it.id }) { s ->
                        // Las similares no están en el proveedor: solo informativas.
                        TvPosterCard(
                            title = s.title.ifBlank { s.name },
                            imageUrl = TmdbClient.posterUrl(s.posterPath),
                            rating = s.voteAverage.takeIf { it > 0 },
                            onClick = {},
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ---------------- Detalle serie (TV, estilo TiviMate) ----------------

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvSeriesDetail(
    seriesId: Int,
    folderName: String?,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<SeriesInfoResponse?>(null) }
    var tmdb by remember { mutableStateOf<TmdbMedia?>(null) }
    var isFav by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    val session = repo.session

    LaunchedEffect(seriesId) {
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
        }
    }

    val si = info
    val title = tmdb?.title ?: si?.info?.name.orEmpty()
    val backdrop = tmdb?.backdropUrl
    val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: si?.info?.plot.orEmpty()
    val year = tmdb?.year ?: TitleCleaner.yearFromDate(si?.info?.releaseDate.orEmpty())
    val rating = tmdb?.rating ?: 0.0
    val seasons = si?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 }.orEmpty()
    val seasonsLabel = if (seasons.isNotEmpty()) {
        "${seasons.size} Temporada" + if (seasons.size == 1) "" else "s"
    } else ""
    val genres = tmdb?.genres?.joinToString(", ").orEmpty()
    val cast = tmdb?.cast?.take(8)?.joinToString(", ") { it.name }.orEmpty()
    val director = tmdb?.director.orEmpty()
    val trailerKey = tmdb?.trailerKey
    val metaLine = listOfNotNull(
        year?.toString(),
        seasonsLabel.ifBlank { null },
        genres.ifBlank { null },
    ).joinToString(" · ")
    val episodes = selectedSeason?.let { si?.episodes?.get(it).orEmpty() }
        ?.sortedBy { it.episodeNum }.orEmpty()

    fun playEpisode(ep: XtreamEpisode, season: String?) {
        val epTitle = ep.title.ifBlank { "Episodio ${ep.episodeNum}" }
        ExternalPlayer.play(
            context, container,
            repo.episodeUrl(ep.id, ep.containerExtension),
            "$title — $epTitle",
            "ep:${ep.id}",
            ep.info.movieImage.ifBlank { tmdb?.posterUrl ?: si?.info?.cover.orEmpty() },
            subTmdbId = tmdb?.tmdbId,
            subSeason = season?.toIntOrNull(),
            subEpisode = ep.episodeNum,
        )
    }

    fun toggleFavorite() {
        val s = session ?: return
        val nfo = si ?: return
        scope.launch {
            isFav = container.favoritesRepository.toggle(
                FavoriteEntity(
                    serverId = s.server.id,
                    kind = "series",
                    refId = seriesId.toString(),
                    name = nfo.info.name,
                    imageUrl = tmdb?.posterUrl ?: nfo.info.cover,
                ),
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MtvBg),
    ) {
        // Backdrop con degradado + info encima.
        item {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                AsyncImage(
                    model = backdrop?.ifBlank { null },
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MtvSurfaceVariant),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MtvBg),
                            startY = 200f,
                        ),
                    ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 36.dp, end = 36.dp, bottom = 8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvRatingBadge(rating)
                        if (metaLine.isNotBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Text(metaLine, style = MaterialTheme.typography.titleMedium, color = MtvOnBg)
                        }
                    }
                    if (cast.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text("Reparto ", style = MaterialTheme.typography.bodyLarge, color = MtvOnVariant)
                            Text(
                                cast,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MtvOnBg,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (director.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("Director ", style = MaterialTheme.typography.bodyLarge, color = MtvOnVariant)
                            Text(director, style = MaterialTheme.typography.bodyLarge, color = MtvOnBg)
                        }
                    }
                }
            }
        }
        // Sinopsis.
        if (overview.isNotBlank()) {
            item {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MtvOnBg,
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
                )
            }
        }
        // Chip de carpeta.
        if (!folderName.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MtvGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(folderName, style = MaterialTheme.typography.titleMedium, color = MtvOnBg)
                }
            }
        }
        // Botones de acción.
        item {
            Row(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 16.dp),
            ) {
                val firstEp = seasons.firstOrNull()?.let { si?.episodes?.get(it)?.minByOrNull { e -> e.episodeNum } }
                if (firstEp != null) {
                    TvDetailAction(
                        label = "Reproducir",
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        onClick = { playEpisode(firstEp, seasons.firstOrNull()) },
                    )
                }
                if (trailerKey != null) {
                    TvDetailAction(
                        label = "Trailer",
                        icon = Icons.Default.Subtitles,
                        onClick = { openYoutube(context, trailerKey) },
                    )
                }
                TvDetailAction(
                    label = if (isFav) "En mi lista" else "Agregar a mi lista",
                    icon = Icons.Default.Folder,
                    onClick = { toggleFavorite() },
                )
            }
        }
        // Temporadas.
        if (seasons.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Temporada ${selectedSeason ?: ""}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MtvOnBg,
                        modifier = Modifier.weight(1f),
                    )
                    if (seasons.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons, key = { it }) { s ->
                                val sel = s == selectedSeason
                                Button(onClick = { selectedSeason = s }) {
                                    Text(
                                        "T$s",
                                        color = if (sel) Color.White else MtvOnBg,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(episodes, key = { "${it.id}:${it.episodeNum}" }) { ep ->
                        TvEpisodeCard(
                            episode = ep,
                            onClick = { playEpisode(ep, selectedSeason) },
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvEpisodeCard(
    episode: XtreamEpisode,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(280.dp),
    ) {
        Column {
            AsyncImage(
                model = episode.info.movieImage.ifBlank { null },
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MtvSurfaceVariant),
            )
            Text(
                "E${episode.episodeNum} · ${episode.title.ifBlank { "Episodio ${episode.episodeNum}" }}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MtvOnBg,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvServersScreen(
    onConnected: () -> Unit,
    addMode: Boolean = false,
    onBack: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var autoLogin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val secure = container.securePrefs
        val skip = LoginFlowState.skipAutoLoginOnce
            .also { LoginFlowState.skipAutoLoginOnce = false }

        // Credenciales para el auto-login: primero las cifradas del último
        // servidor; si no hay, se migran las viejas en plano (UserPrefs).
        var autoCreds: Pair<String, String>? = null
        if (!skip) {
            val lastId = secure.getLastServerId()
            autoCreds = if (lastId > 0) secure.getCredentials(lastId) else null
            if (autoCreds == null) {
                val oldUser = container.userPrefs.getUsername()
                val oldPass = container.userPrefs.getPassword()
                if (oldUser.isNotBlank() && oldPass.isNotBlank()) {
                    autoCreds = oldUser to oldPass
                } else {
                    username = oldUser
                    password = oldPass
                }
            }
        } else {
            // Logout: precargar el formulario con lo recordado, sin auto-login.
            val lastId = secure.getLastServerId()
            val remembered = if (lastId > 0) secure.getCredentials(lastId) else null
            username = remembered?.first ?: container.userPrefs.getUsername()
            password = remembered?.second ?: container.userPrefs.getPassword()
        }

        // Login automático silencioso con las credenciales guardadas.
        // En modo agregar no hay auto-login: es para guardar OTRO usuario.
        if (addMode) return@LaunchedEffect
        val (autoUser, autoPass) = autoCreds ?: return@LaunchedEffect
        autoLogin = true
        when (val result = container.xtreamRepository.loginAuto(autoUser, autoPass)) {
            is LoginResult.Ok -> {
                val row = container.serverRepository.getOrCreateSingle()
                val updated = row.copy(
                    name = "MTV",
                    url = result.session.baseUrl,
                    username = autoUser,
                    password = "",
                )
                container.serverRepository.upsert(updated)
                container.xtreamRepository.updateSessionServer(updated)
                // Guardar cifrado (migra las viejas en plano) + recordar servidor.
                secure.saveCredentials(row.id, autoUser, autoPass)
                secure.setLastServerId(row.id)
                container.userPrefs.setLastServerId(row.id)
                container.userPrefs.clearCredentials()
                onConnected()
            }
            else -> {
                // Falla: mostrar el formulario con los datos para reintentar.
                username = autoUser
                password = autoPass
                autoLogin = false
                error = "No se pudo conectar automáticamente. Revisá tus datos."
            }
        }
    }

    fun connect() {
        val user = username.trim()
        if (user.isBlank() || password.isBlank()) {
            error = "Ingresá tu usuario y contraseña."
            return
        }
        loading = true
        error = null
        scope.launch {
            when (val result = container.xtreamRepository.loginAuto(user, password)) {
                is LoginResult.Ok -> {
                    if (addMode) {
                        // Multi-usuario: crea OTRA fila para el nuevo usuario.
                        val host = result.session.baseUrl.substringAfter("://").substringBefore("/")
                        val newId = container.serverRepository.upsert(
                            com.mtv.iptv.data.local.db.ServerEntity(
                                name = "$user @ $host",
                                url = result.session.baseUrl,
                                username = user,
                                password = "",
                            )
                        )
                        val saved = container.serverRepository.getById(newId)
                        if (saved != null) container.xtreamRepository.updateSessionServer(saved)
                        container.securePrefs.saveCredentials(newId, user, password)
                        container.securePrefs.setLastServerId(newId)
                        container.userPrefs.setLastServerId(newId)
                        container.userPrefs.clearCredentials()
                        loading = false
                        onConnected()
                    } else {
                        // Fila única estable: se actualiza con la URL efectiva.
                        val row = container.serverRepository.getOrCreateSingle()
                        val updated = row.copy(
                            name = "MTV",
                            url = result.session.baseUrl,
                            username = user,
                            password = "",
                        )
                        container.serverRepository.upsert(updated)
                        container.xtreamRepository.updateSessionServer(updated)
                        container.securePrefs.saveCredentials(row.id, user, password)
                        container.securePrefs.setLastServerId(row.id)
                        container.userPrefs.setLastServerId(row.id)
                        container.userPrefs.clearCredentials()
                        loading = false
                        onConnected()
                    }
                }
                LoginResult.AuthFailed -> {
                    loading = false
                    error = "Usuario o contraseña inválidos."
                }
                is LoginResult.NetworkError -> {
                    loading = false
                    error = "No se pudo conectar con el servidor. Revisá tu conexión."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MtvBg)
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (addMode) {
                Button(onClick = onBack) { Text("Atrás") }
                Spacer(Modifier.width(16.dp))
            }
            Text(
                if (addMode) "Agregar usuario" else "MTV",
                style = MaterialTheme.typography.displaySmall,
                color = MtvRed,
            )
        }
        Spacer(Modifier.height(24.dp))
        if (autoLogin) {
            Text("Conectando…", style = MaterialTheme.typography.headlineSmall, color = MtvOnBg)
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            Spacer(Modifier.height(20.dp))
            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodyLarge, color = MtvRed)
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = ::connect, enabled = !loading) {
                Text(if (loading) "Conectando…" else "Conectar")
            }
        }
    }
}

// ---------------- Configuración (TV) ----------------

/**
 * Configuración en TV: reusa el contenido jerárquico de [SettingsContent].
 * Hereda el tema MTV del scaffold (sin envoltorio propio).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    onServers: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
    onSpeedTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Atrás") }
            Spacer(Modifier.width(16.dp))
            Text("Configuración", style = MaterialTheme.typography.headlineSmall, color = MtvOnBg)
        }
        Spacer(Modifier.height(16.dp))
        SettingsContent(
            onServers = onServers,
            onLogout = onLogout,
            onHome = onHome,
            onAddUser = onAddUser,
            onSpeedTest = onSpeedTest,
        )
    }
}
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val module = container.downloadModule
    val entradas by module.tracker.entradas.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Atrás") }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Mis descargas",
                    style = MaterialTheme.typography.displaySmall,
                    color = MtvOnBg,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (entradas.isEmpty()) {
            item {
                Text(
                    "No tenés descargas todavía",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MtvOnVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        } else {
            items(entradas, key = { it.id }) { entry ->
                TvDownloadRow(
                    entry = entry,
                    onPlay = {
                        ExternalPlayer.play(context, container, entry.url, entry.title, entry.id, entry.imageUrl
                        )
                    },
                    onDelete = { module.removeDownload(entry.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvDownloadRow(
    entry: DownloadEntry,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = entry.imageUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .background(MtvSurfaceVariant, RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MtvOnBg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val estado = when (val e = entry.estado) {
                    is EstadoDescarga.Descargando -> "Descargando… ${e.progreso}%"
                    EstadoDescarga.Descargado -> "Descargado"
                    EstadoDescarga.Error -> "Error en la descarga"
                    EstadoDescarga.NoDescargado -> ""
                }
                if (estado.isNotBlank()) {
                    Text(estado, style = MaterialTheme.typography.bodyMedium, color = MtvOnVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onPlay) { Text("Reproducir") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDelete) { Text("Borrar") }
        }
    }
}
