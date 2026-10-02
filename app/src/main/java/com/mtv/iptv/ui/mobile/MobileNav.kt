package com.mtv.iptv.ui.mobile

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val SERVERS = "servers"
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val BROWSE = "browse/{kind}/{categoryId}/{categoryName}"
    const val VOD = "vod/{streamId}"
    const val SERIES = "series/{seriesId}"

    fun browse(kind: String, categoryId: String, categoryName: String) =
        "browse/$kind/$categoryId/${Uri.encode(categoryName)}"

    fun vod(streamId: Int) = "vod/$streamId"
    fun series(seriesId: Int) = "series/$seriesId"
}

@Composable
fun MobileNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SERVERS) {
        composable(Routes.SERVERS) {
            ServersScreen(
                onConnected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SERVERS) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onBrowse = { kind, categoryId, categoryName ->
                    navController.navigate(Routes.browse(kind, categoryId, categoryName))
                },
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
                onFavorites = { navController.navigate(Routes.FAVORITES) },
                onLogout = {
                    navController.navigate(Routes.SERVERS) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
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
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
            )
        }
        composable(
            route = Routes.VOD,
            arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
        ) { entry ->
            VodDetailScreen(
                streamId = entry.arguments?.getInt("streamId") ?: 0,
                onBack = { navController.popBackStack() },
                onVod = { navController.navigate(Routes.vod(it)) },
            )
        }
        composable(
            route = Routes.SERIES,
            arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
        ) { entry ->
            SeriesDetailScreen(
                seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
            )
        }
    }
}
