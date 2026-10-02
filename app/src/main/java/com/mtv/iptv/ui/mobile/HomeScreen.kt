package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtv.iptv.PlayerActivity
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.remote.xtream.XtreamLiveStream
import com.mtv.iptv.data.remote.xtream.XtreamSeries
import com.mtv.iptv.data.remote.xtream.XtreamVodStream
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSection: (String) -> Unit,
    onVod: (Int) -> Unit,
    onSeries: (Int) -> Unit,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
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
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (repo.session == null) {
            onLogout()
            return@LaunchedEffect
        }
        CrashReporter.log(context, "home", "inicio carga")
        try {
            continueWatching = container.playbackRepository.recent(15)
                .filter { it.url.isNotBlank() }
            val liveCats = repo.getLiveCategories()
            CrashReporter.log(context, "home", "liveCats=${liveCats.size}")
            if (liveCats.isNotEmpty()) {
                liveItems = repo.getLiveStreams(liveCats.first().categoryId).take(25)
                CrashReporter.log(context, "home", "liveItems=${liveItems.size}")
            }
            val vodCats = repo.getVodCategories()
            CrashReporter.log(context, "home", "vodCats=${vodCats.size}")
            if (vodCats.isNotEmpty()) {
                vodItems = repo.getVodStreams(vodCats.first().categoryId).take(25)
                CrashReporter.log(context, "home", "vodItems=${vodItems.size}")
            }
            val seriesCats = repo.getSeriesCategories()
            CrashReporter.log(context, "home", "seriesCats=${seriesCats.size}")
            if (seriesCats.isNotEmpty()) {
                seriesItems = repo.getSeries(seriesCats.first().categoryId).take(25)
                CrashReporter.log(context, "home", "seriesItems=${seriesItems.size}")
            }
            CrashReporter.log(context, "home", "carga completa OK")
        } catch (e: Exception) {
            CrashReporter.log(context, "home", "ERROR ${e::class.java.simpleName}: ${e.message}")
            error = "No se pudo cargar el contenido."
        }
        loading = false
    }

    fun logout() {
        scope.launch {
            repo.logout()
            LoginFlowState.skipAutoLoginOnce = true
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MTV") },
                actions = {
                    IconButton(onClick = onFavorites) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favoritos")
                    }
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "Mis descargas")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                    IconButton(onClick = { logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> LoadingBox(Modifier.padding(padding))
            error != null -> ErrorBox(
                message = error!!,
                onRetry = { logout() },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (continueWatching.isNotEmpty()) {
                    item { SectionHeader("Seguir viendo") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(continueWatching, key = { it.mediaKey }) { item ->
                                ContinueWatchingCard(
                                    imageUrl = item.imageUrl.ifBlank { null },
                                    name = item.name,
                                    positionMs = item.positionMs,
                                    durationMs = item.durationMs,
                                    onClick = {
                                        PlayerActivity.start(context, item.url, item.name, item.mediaKey, item.imageUrl)
                                    },
                                )
                            }
                        }
                    }
                }
                if (liveItems.isNotEmpty()) {
                    item {
                        SectionHeader("En vivo") {
                            onSection("live")
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(liveItems, key = { "${it.streamId}:${it.name}" }) { s ->
                                PosterCard(
                                    imageUrl = s.streamIcon.ifBlank { null },
                                    title = s.name,
                                    onClick = {
                                        PlayerActivity.start(
                                            context,
                                            repo.liveUrl(s.streamId),
                                            s.name,
                                            "live:${s.streamId}",
                                            s.streamIcon,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                if (vodItems.isNotEmpty()) {
                    item {
                        SectionHeader("Películas") {
                            onSection("vod")
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(vodItems, key = { "${it.streamId}:${it.name}" }) { v ->
                                PosterCard(
                                    imageUrl = v.streamIcon.ifBlank { null },
                                    title = v.name,
                                    onClick = { onVod(v.streamId) },
                                )
                            }
                        }
                    }
                }
                if (seriesItems.isNotEmpty()) {
                    item {
                        SectionHeader("Series") {
                            onSection("series")
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(seriesItems, key = { "${it.seriesId}:${it.name}" }) { s ->
                                PosterCard(
                                    imageUrl = s.cover.ifBlank { null },
                                    title = s.name,
                                    onClick = { onSeries(s.seriesId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
