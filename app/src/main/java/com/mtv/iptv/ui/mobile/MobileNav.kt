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
    const val FAVORITES = "favorites?kind={kind}"
    const val SECTION = "section/{kind}"
    const val BROWSE = "browse/{kind}/{categoryId}/{categoryName}"
    const val VOD = "vod/{streamId}"
    const val SERIES = "series/{seriesId}"
    const val ACTOR = "actor/{personId}"
    const val COMPANY = "company/{companyId}"

    fun favorites(kind: String? = null) =
        if (kind == null) "favorites" else "favorites?kind=$kind"

    fun section(kind: String) = "section/$kind"

    fun browse(kind: String, categoryId: String, categoryName: String) =
        "browse/$kind/$categoryId/${Uri.encode(categoryName)}"

    fun vod(streamId: Int) = "vod/$streamId"
    fun series(seriesId: Int) = "series/$seriesId"
    fun actor(personId: Int) = "actor/$personId"
    fun company(companyId: Int) = "company/$companyId"
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
                onSection = { kind -> navController.navigate(Routes.section(kind)) },
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
                onFavorites = { navController.navigate(Routes.favorites()) },
                onLogout = {
                    navController.navigate(Routes.SERVERS) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
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
                onActor = { navController.navigate(Routes.actor(it)) },
                onCompany = { navController.navigate(Routes.company(it)) },
            )
        }
        composable(
            route = Routes.SERIES,
            arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
        ) { entry ->
            SeriesDetailScreen(
                seriesId = entry.arguments?.getInt("seriesId") ?: 0,
                onBack = { navController.popBackStack() },
                onActor = { navController.navigate(Routes.actor(it)) },
                onCompany = { navController.navigate(Routes.company(it)) },
            )
        }
        composable(
            route = Routes.ACTOR,
            arguments = listOf(navArgument("personId") { type = NavType.IntType }),
        ) { entry ->
            ActorDetailScreen(
                personId = entry.arguments?.getInt("personId") ?: 0,
                onBack = { navController.popBackStack() },
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
            )
        }
        composable(
            route = Routes.COMPANY,
            arguments = listOf(navArgument("companyId") { type = NavType.IntType }),
        ) { entry ->
            CompanyDetailScreen(
                companyId = entry.arguments?.getInt("companyId") ?: 0,
                onBack = { navController.popBackStack() },
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
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
                onVod = { navController.navigate(Routes.vod(it)) },
                onSeries = { navController.navigate(Routes.series(it)) },
            )
        }
    }
}
