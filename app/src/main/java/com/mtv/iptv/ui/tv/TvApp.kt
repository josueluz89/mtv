package com.mtv.iptv.ui.tv

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
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
import androidx.compose.material3.OutlinedTextField
import coil.compose.AsyncImage
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.data.remote.xtream.SeriesInfoResponse
import com.mtv.iptv.data.remote.xtream.VodInfoResponse
import com.mtv.iptv.data.remote.xtream.XtreamCategory
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
import com.mtv.iptv.ui.mobile.LoginFlowState
import com.mtv.iptv.ui.mobile.SettingsContent
import com.mtv.iptv.ui.speedtest.SpeedTestScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- Navegación principal (TV) ----------------

private data class TvNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tvNavItems = listOf(
    TvNavItem("tv_home", "Inicio", Icons.Default.Home),
    TvNavItem("tv_live", "En vivo", Icons.Default.LiveTv),
    TvNavItem("tv_vod", "Películas", Icons.Default.Movie),
    TvNavItem("tv_series", "Series", Icons.Default.Tv),
    TvNavItem("tv_search", "Buscar", Icons.Default.Search),
    TvNavItem("tv_downloads", "Descargas", Icons.Default.Download),
    TvNavItem("tv_settings", "Ajustes", Icons.Default.Settings),
)

/**
 * Raíz de la UI de TV: tema MTV (oscuro de marca) + navegación.
 * El login no lleva sidebar; todo lo demás va dentro de [TvScaffold]
 * con la barra lateral izquierda.
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
                        navController.navigate("tv_home") {
                            popUpTo("tv_servers") { inclusive = true }
                        }
                    },
                )
            }
            composable("tv_home") {
                TvScaffold(navController, "tv_home") {
                    TvHomeScreen(
                        onSection = { kind ->
                            navController.navigate(
                                when (kind) {
                                    "live" -> "tv_live"
                                    "vod" -> "tv_vod"
                                    else -> "tv_series"
                                },
                            )
                        },
                        onVod = { navController.navigate("tv_vod_detail/$it") },
                        onSeries = { navController.navigate("tv_series_detail/$it") },
                    )
                }
            }
            composable("tv_live") {
                TvScaffold(navController, "tv_live") {
                    TvLiveFoldersScreen(onFolder = { cat ->
                        navController.navigate(
                            "tv_items/live/${cat.categoryId}/${Uri.encode(cat.categoryName)}",
                        )
                    })
                }
            }
            composable("tv_vod") {
                TvScaffold(navController, "tv_vod") {
                    TvMovieFoldersScreen(onFolder = { cat ->
                        navController.navigate(
                            "tv_items/vod/${cat.categoryId}/${Uri.encode(cat.categoryName)}",
                        )
                    })
                }
            }
            composable("tv_series") {
                TvScaffold(navController, "tv_series") {
                    TvSeriesFoldersScreen(onFolder = { cat ->
                        navController.navigate(
                            "tv_items/series/${cat.categoryId}/${Uri.encode(cat.categoryName)}",
                        )
                    })
                }
            }
            composable("tv_search") {
                TvScaffold(navController, "tv_search") {
                    TvSearchScreen(
                        onBack = { navController.popBackStack() },
                        onVod = { navController.navigate("tv_vod_detail/$it") },
                        onSeries = { navController.navigate("tv_series_detail/$it") },
                    )
                }
            }
            composable("tv_downloads") {
                TvScaffold(navController, "tv_downloads") {
                    TvDownloadsScreen(onBack = { navController.popBackStack() })
                }
            }
            composable("tv_settings") {
                TvScaffold(navController, "tv_settings") {
                    TvSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onServers = {
                            navController.navigate("tv_servers") {
                                popUpTo("tv_home") { inclusive = true }
                            }
                        },
                        onLogout = {
                            LoginFlowState.skipAutoLoginOnce = true
                            navController.navigate("tv_servers") {
                                popUpTo("tv_home") { inclusive = true }
                            }
                        },
                        onHome = {
                            navController.navigate("tv_home") {
                                popUpTo("tv_home") { inclusive = true }
                            }
                        },
                        onAddUser = { navController.navigate("tv_add_user") },
                        onSpeedTest = { navController.navigate("tv_speedtest") },
                    )
                }
            }
            composable("tv_add_user") {
                TvServersScreen(
                    addMode = true,
                    onConnected = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("tv_speedtest") {
                SpeedTestScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "tv_items/{kind}/{categoryId}/{categoryName}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType },
                    navArgument("categoryName") { type = NavType.StringType },
                ),
            ) { entry ->
                val kind = entry.arguments?.getString("kind").orEmpty()
                val section = when (kind) {
                    "live" -> "tv_live"
                    "vod" -> "tv_vod"
                    else -> "tv_series"
                }
                TvScaffold(navController, section) {
                    TvCategoryItemsScreen(
                        kind = kind,
                        categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                        categoryName = entry.arguments?.getString("categoryName").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onVod = { navController.navigate("tv_vod_detail/$it") },
                        onSeries = { navController.navigate("tv_series_detail/$it") },
                    )
                }
            }
            composable(
                "tv_vod_detail/{streamId}",
                arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
            ) { entry ->
                TvScaffold(navController, "tv_vod") {
                    TvVodDetailsScreen(
                        streamId = entry.arguments?.getInt("streamId") ?: 0,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                "tv_series_detail/{seriesId}",
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
            ) { entry ->
                TvScaffold(navController, "tv_series") {
                    TvSeriesDetailsScreen(
                        seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

// ---------------- Scaffold con sidebar izquierda ----------------

/**
 * Estructura de las pantallas principales de TV: barra lateral izquierda
 * fija (240dp) con las secciones + contenido a la derecha.
 * Todo navegable con D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvScaffold(
    navController: NavController,
    selectedRoute: String,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(MtvBg)) {
        TvSidebar(
            selectedRoute = selectedRoute,
            onSelect = { route ->
                if (route != selectedRoute) {
                    navController.navigate(route) {
                        popUpTo("tv_home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            modifier = Modifier.width(240.dp).fillMaxHeight(),
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSidebar(
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MtvSurface)
            .padding(vertical = 28.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "MTV",
            style = MaterialTheme.typography.headlineLarge,
            color = MtvRed,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(8.dp))
        tvNavItems.forEach { item ->
            TvSidebarItem(
                selected = item.route == selectedRoute,
                item = item,
                onClick = { onSelect(item.route) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSidebarItem(
    selected: Boolean,
    item: TvNavItem,
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
            contentDescription = null,
            tint = if (selected) Color.White else MtvOnVariant,
            modifier = Modifier.size(22.dp),
        )
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

// ---------------- Inicio (TV) ----------------

/**
 * Pantalla de inicio en TV: "Seguir viendo" + filas de En vivo, Películas y
 * Series. Las secciones completas viven en la sidebar.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvHomeScreen(
    onSection: (String) -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var continueWatching by remember { mutableStateOf<List<PlaybackEntity>>(emptyList()) }
    var liveItems by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var vodItems by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var seriesItems by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            continueWatching = container.playbackRepository.recent(15).filter { it.url.isNotBlank() }
            val liveCats = repo.getLiveCategories()
            if (liveCats.isNotEmpty()) liveItems = repo.getLiveStreams(liveCats.first().categoryId).take(25)
            val vodCats = repo.getVodCategories()
            if (vodCats.isNotEmpty()) vodItems = repo.getVodStreams(vodCats.first().categoryId).take(25)
            val seriesCats = repo.getSeriesCategories()
            if (seriesCats.isNotEmpty()) seriesItems = repo.getSeries(seriesCats.first().categoryId).take(25)
        } catch (e: Exception) {
            // filas vacías
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (continueWatching.isNotEmpty()) {
            item { Text("Seguir viendo", style = MaterialTheme.typography.headlineSmall, color = MtvOnBg) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continueWatching, key = { it.mediaKey }) { item ->
                        TvMediaCard(title = item.name, imageUrl = item.imageUrl.ifBlank { null }) {
                            ExternalPlayer.play(context, container, item.url, item.name, item.mediaKey, item.imageUrl)
                        }
                    }
                }
            }
        }
        if (liveItems.isNotEmpty()) {
            item {
                TvSectionHeader(title = "En vivo", onSeeAll = { onSection("live") })
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(liveItems, key = { "${it.streamId}:${it.name}" }) { s ->
                        TvMediaCard(title = s.name, imageUrl = s.streamIcon.ifBlank { null }, aspectRatio = 1f) {
                            ExternalPlayer.play(context, container, repo.liveUrl(s.streamId), s.name, "live:${s.streamId}", s.streamIcon)
                        }
                    }
                }
            }
        }
        if (vodItems.isNotEmpty()) {
            item {
                TvSectionHeader(title = "Películas", onSeeAll = { onSection("vod") })
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(vodItems, key = { "${it.streamId}:${it.name}" }) { v ->
                        TvMediaCard(title = v.name, imageUrl = v.streamIcon.ifBlank { null }) {
                            onVod(v.streamId)
                        }
                    }
                }
            }
        }
        if (seriesItems.isNotEmpty()) {
            item {
                TvSectionHeader(title = "Series", onSeeAll = { onSection("series") })
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(seriesItems, key = { "${it.seriesId}:${it.name}" }) { s ->
                        TvMediaCard(title = s.name, imageUrl = s.cover.ifBlank { null }) {
                            onSeries(s.seriesId)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MtvOnBg,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSeeAll) { Text("Ver todo") }
    }
}

// ---------------- Carpetas (TV): vista principal de cada sección ----------------

/**
 * En vivo en TV: las categorías del proveedor como carpetas (vista
 * principal), cada una con su conteo real de canales. Igual que el móvil.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLiveFoldersScreen(onFolder: (XtreamCategory) -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var folders by remember { mutableStateOf<List<Pair<XtreamCategory, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val categories = repo.getLiveCategories().distinctBy { it.categoryId }
            val all = repo.getLiveStreams(null)
            folders = withContext(Dispatchers.Default) {
                val counts = all.groupingBy { it.categoryId }.eachCount()
                categories.mapNotNull { cat ->
                    val total = counts[cat.categoryId] ?: 0
                    if (total > 0) cat to total else null
                }
            }
        } catch (e: Exception) {
            folders = emptyList()
        }
        loading = false
    }

    TvFolderGrid(
        title = "En vivo",
        folders = folders,
        loading = loading,
        countLabel = { n -> "$n canales" },
        onFolder = onFolder,
    )
}

/**
 * Películas en TV: las categorías VOD del proveedor como carpetas (vista
 * principal), cada una con su conteo real de títulos. Igual que el móvil.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMovieFoldersScreen(onFolder: (XtreamCategory) -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var folders by remember { mutableStateOf<List<Pair<XtreamCategory, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
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
            folders = emptyList()
        }
        loading = false
    }

    TvFolderGrid(
        title = "Películas",
        folders = folders,
        loading = loading,
        countLabel = { n -> "$n títulos" },
        onFolder = onFolder,
    )
}

/**
 * Series en TV: las categorías del proveedor como carpetas (vista principal),
 * cada una con su conteo real de series. Igual que el móvil.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSeriesFoldersScreen(onFolder: (XtreamCategory) -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var folders by remember { mutableStateOf<List<Pair<XtreamCategory, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
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
            folders = emptyList()
        }
        loading = false
    }

    TvFolderGrid(
        title = "Series",
        folders = folders,
        loading = loading,
        countLabel = { n -> "$n series" },
        onFolder = onFolder,
    )
}

/** Cuadrícula de carpetas con conteo, navegable con D-pad. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFolderGrid(
    title: String,
    folders: List<Pair<XtreamCategory, Int>>,
    loading: Boolean,
    countLabel: (Int) -> String,
    onFolder: (XtreamCategory) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = MtvOnBg)
        Spacer(Modifier.height(4.dp))
        Text(
            if (loading) "Cargando…" else "${folders.size} carpetas",
            style = MaterialTheme.typography.bodyLarge,
            color = MtvOnVariant,
        )
        Spacer(Modifier.height(20.dp))
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando…", style = MaterialTheme.typography.headlineSmall, color = MtvOnVariant)
                }
            }
            folders.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay contenido.", style = MaterialTheme.typography.headlineSmall, color = MtvOnVariant)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(folders, key = { it.first.categoryId }) { (cat, count) ->
                        TvFolderCard(
                            name = cat.categoryName.ifBlank { "Sin nombre" },
                            countText = countLabel(count),
                            onClick = { onFolder(cat) },
                        )
                    }
                }
            }
        }
    }
}

/** Tarjeta de carpeta: icono dorado + nombre + conteo, con foco visible. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFolderCard(name: String, countText: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MtvGold,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MtvOnBg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    countText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MtvOnVariant,
                )
            }
        }
    }
}

// ---------------- Tarjetas (TV) ----------------

/**
 * Tarjeta de contenido con póster y título, al estilo del móvil.
 * La Card de tv-material3 ya da escala/borde al recibir foco del D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    aspectRatio: Float = 2f / 3f,
) {
    Card(onClick = onClick, modifier = Modifier.width(160.dp)) {
        Column {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(MtvSurfaceVariant),
            )
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

// ---------------- Login (TV) ----------------

/**
 * Login simplificado: solo usuario y contraseña. Los servidores (DNS) están
 * ocultos en el código y se prueban en orden hasta que uno acepta las
 * credenciales. Usa OutlinedTextField de material3 para que el input funcione
 * con el control remoto.
 */
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

// ---------------- Detalle película (TV) ----------------

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvVodDetailsScreen(streamId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var info by remember { mutableStateOf<VodInfoResponse?>(null) }
    var tmdb by remember { mutableStateOf<TmdbMedia?>(null) }

    LaunchedEffect(streamId) {
        try {
            val vi = repo.getVodInfo(streamId)
            info = vi
            tmdb = container.tmdbRepository.findMovie(vi.movieData.name, TitleCleaner.yearFromDate(vi.info.releasedate))
        } catch (e: Exception) {
        }
    }

    val vi = info
    val title = tmdb?.title ?: vi?.movieData?.name.orEmpty()
    val poster = tmdb?.posterUrl ?: vi?.info?.movieImage.orEmpty()
    val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: vi?.info?.plot.orEmpty()
    val year = tmdb?.year ?: TitleCleaner.yearFromDate(vi?.info?.releasedate.orEmpty())

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
                    title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MtvOnBg,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Row {
                AsyncImage(
                    model = poster.ifBlank { null },
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(2f / 3f)
                        .background(MtvSurfaceVariant, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (year != null) {
                        Text("$year", style = MaterialTheme.typography.headlineSmall, color = MtvOnVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (overview.isNotBlank()) {
                        Text(overview, style = MaterialTheme.typography.bodyLarge, color = MtvOnBg)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        if (vi != null) {
                            ExternalPlayer.play(context, container,
                                repo.vodUrl(vi.movieData.streamId, vi.movieData.containerExtension),
                                vi.movieData.name,
                                "vod:${vi.movieData.streamId}",
                                tmdb?.posterUrl ?: vi.info.movieImage,
                                subTmdbId = tmdb?.tmdbId,
                            )
                        }
                    }) { Text("▶ Reproducir") }
                }
            }
        }
    }
}

// ---------------- Detalle serie (TV) ----------------

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvSeriesDetailsScreen(seriesId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var info by remember { mutableStateOf<SeriesInfoResponse?>(null) }
    var tmdb by remember { mutableStateOf<TmdbMedia?>(null) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seriesId) {
        try {
            val si = repo.getSeriesInfo(seriesId)
            info = si
            tmdb = container.tmdbRepository.findSeries(si.info.name)
            selectedSeason = si.episodes.keys.sortedBy { it.toIntOrNull() ?: 0 }.firstOrNull()
        } catch (e: Exception) {
        }
    }

    val si = info
    val title = tmdb?.title ?: si?.info?.name.orEmpty()
    val poster = tmdb?.posterUrl ?: si?.info?.cover.orEmpty()
    val overview = tmdb?.overview?.takeIf { it.isNotBlank() } ?: si?.info?.plot.orEmpty()
    val seasons = si?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 }.orEmpty()
    val episodes = selectedSeason?.let { si?.episodes?.get(it).orEmpty() }?.sortedBy { it.episodeNum }.orEmpty()

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
                    title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MtvOnBg,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Row {
                AsyncImage(
                    model = poster.ifBlank { null },
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(2f / 3f)
                        .background(MtvSurfaceVariant, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (overview.isNotBlank()) {
                        Text(overview, style = MaterialTheme.typography.bodyLarge, color = MtvOnBg)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (seasons.size > 1) {
                        Text("Temporada", style = MaterialTheme.typography.titleMedium, color = MtvOnBg)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { s ->
                                Button(onClick = { selectedSeason = s }) {
                                    Text(if (s == selectedSeason) "● $s" else s)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (episodes.isNotEmpty()) {
            item { Text("Episodios", style = MaterialTheme.typography.headlineSmall, color = MtvOnBg) }
            items(episodes, key = { "${it.id.ifBlank { "ep" }}:${it.episodeNum}:${it.title}" }) { ep ->
                Card(
                    onClick = {
                        ExternalPlayer.play(context, container,
                            repo.episodeUrl(ep.id, ep.containerExtension),
                            "$title — ${ep.title.ifBlank { "Episodio ${ep.episodeNum}" }}",
                            "ep:${ep.id}",
                            ep.info.movieImage.ifBlank { poster },
                            subTmdbId = tmdb?.tmdbId,
                            subSeason = selectedSeason?.toIntOrNull(),
                            subEpisode = ep.episodeNum,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = ep.info.movieImage.ifBlank { null },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(16f / 9f)
                                .background(MtvSurfaceVariant, RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "${ep.episodeNum}. ${ep.title.ifBlank { "Episodio ${ep.episodeNum}" }}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MtvOnBg,
                            )
                            if (ep.info.plot.isNotBlank()) {
                                Text(
                                    ep.info.plot,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MtvOnVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Items de una categoría (TV) ----------------

private fun tvSectionTitle(kind: String): String = when (kind) {
    "live" -> "En vivo"
    "vod" -> "Películas"
    "series" -> "Series"
    else -> "Explorar"
}

/**
 * Items de una categoría en TV: fila de botones de categoría (para cambiar)
 * y fila de tarjetas de contenido, todo navegable con D-pad.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvCategoryItemsScreen(
    kind: String,
    categoryId: String,
    categoryName: String,
    onBack: () -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.xtreamRepository

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCat by remember { mutableStateOf(categoryId) }
    var liveList by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var vodList by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }

    LaunchedEffect(kind) {
        try {
            categories = when (kind) {
                "live" -> repo.getLiveCategories()
                "vod" -> repo.getVodCategories()
                else -> repo.getSeriesCategories()
            }.distinctBy { it.categoryId }
        } catch (e: Exception) {
            categories = emptyList()
        }
    }

    LaunchedEffect(kind, selectedCat) {
        try {
            val cat = selectedCat.takeIf { it != "all" }
            when (kind) {
                "live" -> liveList = repo.getLiveStreams(cat)
                "vod" -> vodList = repo.getVodStreams(cat)
                else -> seriesList = repo.getSeries(cat)
            }
        } catch (e: Exception) {
            // filas vacías
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Atrás") }
                Spacer(Modifier.width(16.dp))
                Text(
                    categoryName.ifBlank { tvSectionTitle(kind) },
                    style = MaterialTheme.typography.displaySmall,
                    color = MtvOnBg,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Button(onClick = { selectedCat = "all" }) {
                        Text(if (selectedCat == "all") "● Todo" else "Todo")
                    }
                }
                items(categories, key = { "${it.categoryId}:${it.categoryName}" }) { cat ->
                    Button(onClick = { selectedCat = cat.categoryId }) {
                        Text(if (selectedCat == cat.categoryId) "● ${cat.categoryName}" else cat.categoryName)
                    }
                }
            }
        }
        item {
            when (kind) {
                "live" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(liveList, key = { "${it.streamId}:${it.name}" }) { s ->
                        TvMediaCard(title = s.name, imageUrl = s.streamIcon.ifBlank { null }, aspectRatio = 1f) {
                            ExternalPlayer.play(context, container,
                                repo.liveUrl(s.streamId),
                                s.name,
                                "live:${s.streamId}",
                                s.streamIcon,
                            )
                        }
                    }
                }
                "vod" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(vodList, key = { "${it.streamId}:${it.name}" }) { v ->
                        TvMediaCard(title = v.name, imageUrl = v.streamIcon.ifBlank { null }) {
                            onVod(v.streamId)
                        }
                    }
                }
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(seriesList, key = { "${it.seriesId}:${it.name}" }) { s ->
                        TvMediaCard(title = s.name, imageUrl = s.cover.ifBlank { null }) {
                            onSeries(s.seriesId)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Descargas (TV) ----------------

/** Lista de descargas con D-pad: reproducir (offline vía caché) y borrar. */
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
