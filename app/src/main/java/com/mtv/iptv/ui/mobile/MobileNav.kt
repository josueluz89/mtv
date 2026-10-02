package com.mtv.iptv.ui.mobile

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtv.iptv.ui.detail.MovieDetailScreen
import com.mtv.iptv.ui.detail.SeriesDetailScreen as V12SeriesDetailScreen
import com.mtv.iptv.ui.downloads.DownloadsScreen
import com.mtv.iptv.ui.home.HomeScreen
import com.mtv.iptv.ui.movies.AllMoviesScreen
import com.mtv.iptv.ui.movies.MoviesScreen
import com.mtv.iptv.ui.library.LibraryScreen
import com.mtv.iptv.ui.search.SearchScreen
import com.mtv.iptv.ui.series.AllSeriesScreen
import com.mtv.iptv.ui.series.SeriesScreen
import com.mtv.iptv.ui.speedtest.SpeedTestScreen
import com.mtv.iptv.ui.tv.LiveTvScreen

object Routes {
    const val SERVERS = "servers"
    const val HOME = "home"

    /** Destinos top-level v1.2 (bottom nav). */
    const val LIVE_TV = "livetv"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SPEEDTEST = "speedtest"

    /** Pantallas de detalle nuevas estilo iMPlayer (Worker B). */
    const val V12_MOVIE = "v12_movie/{streamId}"
    const val V12_SERIE = "v12_serie/{seriesId}"
    const val ALL_MOVIES = "v12_all_movies"
    const val ALL_SERIES = "v12_all_series"
    /** Mi biblioteca: historial + favoritos en un solo lugar. */
    const val LIBRARY = "v12_library"

    const val FAVORITES = "favorites?kind={kind}"
    const val SECTION = "section/{kind}"
    const val BROWSE = "browse/{kind}/{categoryId}/{categoryName}"
    const val DOWNLOADS = "downloads"
    const val ADD_USER = "add_user"
    const val ACTOR = "actor/{personId}"
    const val COMPANY = "company/{companyId}"

    fun favorites(kind: String? = null) =
        if (kind == null) "favorites" else "favorites?kind=$kind"

    fun section(kind: String) = "section/$kind"

    fun browse(kind: String, categoryId: String, categoryName: String) =
        "browse/$kind/$categoryId/${Uri.encode(categoryName)}"

    fun v12movie(streamId: Int) = "v12_movie/$streamId"
    fun v12serie(seriesId: Int) = "v12_serie/$seriesId"
    fun actor(personId: Int) = "actor/$personId"
    fun company(companyId: Int) = "company/$companyId"
}

/** Destinos de la barra inferior v1.2. */
private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Inicio", Icons.Default.Home),
    BottomDestination(Routes.LIVE_TV, "TV en vivo", Icons.Default.LiveTv),
    BottomDestination(Routes.MOVIES, "Películas", Icons.Default.Movie),
    BottomDestination(Routes.SERIES, "Series", Icons.Default.Tv),
    BottomDestination(Routes.LIBRARY, "Biblioteca", Icons.Default.Bookmarks),
    BottomDestination(Routes.SETTINGS, "Ajustes", Icons.Default.Settings),
)

private val topLevelRoutes = bottomDestinations.map { it.route }.toSet()

/** Grafo anidado que agrupa los destinos de la barra inferior. */
private const val MAIN_GRAPH = "main"

@Composable
fun MobileNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                MtvBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { dest -> navController.navigateBottom(dest) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SERVERS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.SERVERS) {
                ServersScreen(
                    onConnected = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SERVERS) { inclusive = true }
                        }
                    },
                )
            }
            navigation(route = MAIN_GRAPH, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onFavorites = { navController.navigate(Routes.favorites()) },
                        onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        onVod = { navController.navigate(Routes.v12movie(it)) },
                        onSeries = { navController.navigate(Routes.v12serie(it)) },
                        onLiveTv = { navController.navigateBottom(Routes.LIVE_TV) },
                        onMovies = { navController.navigateBottom(Routes.MOVIES) },
                        onAllSeries = { navController.navigateBottom(Routes.SERIES) },
                        onLogout = {
                            LoginFlowState.skipAutoLoginOnce = true
                            navController.navigate(Routes.SERVERS) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.LIVE_TV) {
                    LiveTvScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.MOVIES) {
                    MoviesScreen(
                        onBack = { navController.popBackStack() },
                        onFolder = { cat ->
                            navController.navigate(Routes.browse("vod", cat.categoryId, cat.categoryName))
                        },
                        onSeeAll = { navController.navigate(Routes.ALL_MOVIES) },
                    )
                }
                composable(Routes.SERIES) {
                    SeriesScreen(
                        onBack = { navController.popBackStack() },
                        onFolder = { cat ->
                            navController.navigate(Routes.browse("series", cat.categoryId, cat.categoryName))
                        },
                        onSeeAll = { navController.navigate(Routes.ALL_SERIES) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onVod = { navController.navigate(Routes.v12movie(it)) },
                        onSeries = { navController.navigate(Routes.v12serie(it)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onServers = {
                            navController.navigate(Routes.SERVERS) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onLogout = {
                            LoginFlowState.skipAutoLoginOnce = true
                            navController.navigate(Routes.SERVERS) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onAddUser = { navController.navigate(Routes.ADD_USER) },
                        onSpeedTest = { navController.navigate(Routes.SPEEDTEST) },
                    )
                }
            }
            composable(Routes.ALL_MOVIES) {
                AllMoviesScreen(
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                )
            }
            composable(Routes.ALL_SERIES) {
                AllSeriesScreen(
                    onBack = { navController.popBackStack() },
                    onSeries = { navController.navigate(Routes.v12serie(it)) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                    onSeries = { navController.navigate(Routes.v12serie(it)) },
                )
            }
            composable(
                route = Routes.V12_MOVIE,
                arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
            ) { entry ->
                MovieDetailScreen(
                    streamId = entry.arguments?.getInt("streamId") ?: 0,
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                    onActor = { navController.navigate(Routes.actor(it)) },
                    onCompany = { navController.navigate(Routes.company(it)) },
                )
            }
            composable(
                route = Routes.V12_SERIE,
                arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
            ) { entry ->
                V12SeriesDetailScreen(
                    seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                    onBack = { navController.popBackStack() },
                    onActor = { navController.navigate(Routes.actor(it)) },
                    onCompany = { navController.navigate(Routes.company(it)) },
                )
            }
            composable(Routes.SPEEDTEST) {
                SpeedTestScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SECTION,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                val kind = entry.arguments?.getString("kind").orEmpty()
                SectionScreen(
                    kind = kind,
                    onBack = { navController.popBackStack() },
                    onCategory = { k, categoryId, categoryName ->
                        navController.navigate(Routes.browse(k, categoryId, categoryName))
                    },
                    onFavorites = { k -> navController.navigate(Routes.favorites(k)) },
                )
            }
            composable(
                route = Routes.BROWSE,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType },
                    navArgument("categoryName") { type = NavType.StringType },
                ),
            ) { entry ->
                CategoryListScreen(
                    kind = entry.arguments?.getString("kind").orEmpty(),
                    categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                    categoryName = entry.arguments?.getString("categoryName").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                    onSeries = { navController.navigate(Routes.v12serie(it)) },
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ADD_USER) {
                ServersScreen(
                    addMode = true,
                    onConnected = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ACTOR,
                arguments = listOf(navArgument("personId") { type = NavType.IntType }),
            ) { entry ->
                ActorDetailScreen(
                    personId = entry.arguments?.getInt("personId") ?: 0,
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                    onSeries = { navController.navigate(Routes.v12serie(it)) },
                )
            }
            composable(
                route = Routes.COMPANY,
                arguments = listOf(navArgument("companyId") { type = NavType.IntType }),
            ) { entry ->
                CompanyDetailScreen(
                    companyId = entry.arguments?.getInt("companyId") ?: 0,
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                    onSeries = { navController.navigate(Routes.v12serie(it)) },
                )
            }
            composable(
                route = Routes.FAVORITES,
                arguments = listOf(
                    navArgument("kind") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                FavoritesScreen(
                    kindFilter = entry.arguments?.getString("kind"),
                    onBack = { navController.popBackStack() },
                    onVod = { navController.navigate(Routes.v12movie(it)) },
                    onSeries = { navController.navigate(Routes.v12serie(it)) },
                )
            }
        }
    }
}

/** Navegación de la barra inferior: una sola copia por destino, con estado. */
private fun NavController.navigateBottom(route: String) {
    navigate(route) {
        popUpTo(MAIN_GRAPH) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MtvBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        bottomDestinations.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = { if (currentRoute != dest.route) onNavigate(dest.route) },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
            )
        }
    }
}
