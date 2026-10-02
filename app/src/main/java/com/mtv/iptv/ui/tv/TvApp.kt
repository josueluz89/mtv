package com.mtv.iptv.ui.tv

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mtv.iptv.PlayerActivity
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.local.db.ServerEntity
import com.mtv.iptv.data.remote.tmdb.TitleCleaner
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbMedia
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.data.remote.xtream.SeriesInfoResponse
import com.mtv.iptv.data.remote.xtream.VodInfoResponse
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.mobile.ServerEditDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp() {
    MaterialTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "tv_servers") {
            composable("tv_servers") {
                TvServersScreen(
                    onConnected = {
                        navController.navigate("tv_browse") {
                            popUpTo("tv_servers") { inclusive = true }
                        }
                    },
                )
            }
            composable("tv_browse") {
                TvBrowseScreen(
                    onVod = { navController.navigate("tv_vod/$it") },
                    onSeries = { navController.navigate("tv_series/$it") },
                    onLogout = {
                        navController.navigate("tv_servers") {
                            popUpTo("tv_browse") { inclusive = true }
                        }
                    },
                )
            }
            composable(
                "tv_vod/{streamId}",
                arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
            ) { entry ->
                TvVodDetailsScreen(
                    streamId = entry.arguments?.getInt("streamId") ?: 0,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                "tv_series/{seriesId}",
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
            ) { entry ->
                TvSeriesDetailsScreen(
                    seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// ---------------- Servidores (TV) ----------------

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvServersScreen(onConnected: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val servers by container.serverRepository.observeAll().collectAsState(initial = emptyList())
    var connectingId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerEntity?>(null) }

    LaunchedEffect(Unit) { container.serverRepository.ensurePresets() }

    fun connect(server: ServerEntity) {
        if (server.username.isBlank() || server.password.isBlank()) {
            error = "Editá el servidor para ingresar tu usuario y contraseña."
            return
        }
        connectingId = server.id
        error = null
        scope.launch {
            when (container.xtreamRepository.login(server)) {
                is LoginResult.Ok -> {
                    container.userPrefs.setLastServerId(server.id)
                    connectingId = null
                    onConnected()
                }
                LoginResult.AuthFailed -> {
                    connectingId = null
                    error = "Usuario o contraseña inválidos."
                }
                is LoginResult.NetworkError -> {
                    connectingId = null
                    error = "No se pudo conectar con el servidor."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("MTV — Servidores", style = MaterialTheme.typography.displaySmall) }
        if (error != null) {
            item { Text(error!!, style = MaterialTheme.typography.bodyLarge) }
        }
        items(servers, key = { it.id }) { server ->
            Card(
                onClick = { if (connectingId == null) connect(server) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.headlineSmall)
                        Text(server.url, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { editing = server; showDialog = true }) {
                        Text("Editar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (connectingId == null) connect(server) }) {
                        Text(if (connectingId == server.id) "Conectando…" else "Conectar")
                    }
                }
            }
        }
        item {
            Button(onClick = { editing = null; showDialog = true }) {
                Text("Agregar servidor")
            }
        }
    }

    if (showDialog) {
        ServerEditDialog(
            server = editing,
            onDismiss = { showDialog = false },
            onSave = { name, url, username, password ->
                scope.launch {
                    val entity = editing?.copy(name = name, url = url, username = username, password = password)
                        ?: ServerEntity(name = name, url = url, username = username, password = password)
                    container.serverRepository.upsert(entity)
                    showDialog = false
                }
            },
        )
    }
}

// ---------------- Explorar (TV) ----------------

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun TvBrowseScreen(
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val repo = container.xtreamRepository

    var continueWatching by remember { mutableStateOf<List<PlaybackEntity>>(emptyList()) }
    var liveItems by remember { mutableStateOf<List<XtreamLiveStream>>(emptyList()) }
    var vodItems by remember { mutableStateOf<List<XtreamVodStream>>(emptyList()) }
    var seriesItems by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (repo.session == null) {
            onLogout()
            return@LaunchedEffect
        }
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MTV", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                Button(onClick = {
                    scope.launch { repo.logout() }
                    onLogout()
                }) { Text("Salir") }
            }
        }
        if (continueWatching.isNotEmpty()) {
            item { Text("Seguir viendo", style = MaterialTheme.typography.headlineSmall) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continueWatching, key = { it.mediaKey }) { item ->
                        TvMediaCard(title = item.name, imageUrl = item.imageUrl.ifBlank { null }) {
                            PlayerActivity.start(context, item.url, item.name, item.mediaKey, item.imageUrl)
                        }
                    }
                }
            }
        }
        if (liveItems.isNotEmpty()) {
            item { Text("En vivo", style = MaterialTheme.typography.headlineSmall) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(liveItems, key = { it.streamId }) { s ->
                        TvMediaCard(title = s.name, imageUrl = s.streamIcon.ifBlank { null }) {
                            PlayerActivity.start(context, repo.liveUrl(s.streamId), s.name, "live:${s.streamId}", s.streamIcon)
                        }
                    }
                }
            }
        }
        if (vodItems.isNotEmpty()) {
            item { Text("Películas", style = MaterialTheme.typography.headlineSmall) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(vodItems, key = { it.streamId }) { v ->
                        TvMediaCard(title = v.name, imageUrl = v.streamIcon.ifBlank { null }) {
                            onVod(v.streamId)
                        }
                    }
                }
            }
        }
        if (seriesItems.isNotEmpty()) {
            item { Text("Series", style = MaterialTheme.typography.headlineSmall) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(seriesItems, key = { it.seriesId }) { s ->
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
fun TvMediaCard(title: String, imageUrl: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(150.dp)) {
        Column {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(2f / 3f),
            )
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(10.dp),
            )
        }
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
                Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
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
                        .aspectRatio(2f / 3f),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (year != null) Text("$year", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    if (overview.isNotBlank()) Text(overview, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        if (vi != null) {
                            PlayerActivity.start(
                                context,
                                repo.vodUrl(vi.movieData.streamId, vi.movieData.containerExtension),
                                vi.movieData.name,
                                "vod:${vi.movieData.streamId}",
                                tmdb?.posterUrl ?: vi.info.movieImage,
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
                Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
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
                        .aspectRatio(2f / 3f),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (overview.isNotBlank()) Text(overview, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    if (seasons.size > 1) {
                        Text("Temporada", style = MaterialTheme.typography.titleMedium)
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
            item { Text("Episodios", style = MaterialTheme.typography.headlineSmall) }
            items(episodes, key = { it.id.ifBlank { "ep-${it.episodeNum}" } }) { ep ->
                Card(
                    onClick = {
                        PlayerActivity.start(
                            context,
                            repo.episodeUrl(ep.id, ep.containerExtension),
                            "$title — ${ep.title.ifBlank { "Episodio ${ep.episodeNum}" }}",
                            "ep:${ep.id}",
                            ep.info.movieImage.ifBlank { poster },
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
                                .aspectRatio(16f / 9f),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "${ep.episodeNum}. ${ep.title.ifBlank { "Episodio ${ep.episodeNum}" }}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (ep.info.plot.isNotBlank()) {
                                Text(
                                    ep.info.plot,
                                    style = MaterialTheme.typography.bodyMedium,
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
