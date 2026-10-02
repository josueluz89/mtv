package com.mtv.iptv.ui.settings

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Navegación interna de Ajustes (jerárquica).
 *
 * La raíz ([SettingsRoutes.ROOT]) lista los grupos; cada grupo abre su
 * pantalla secundaria con TopAppBar y botón Atrás (popBackStack interno).
 * El Atrás de la pantalla que contiene a SettingsNavHost lo maneja el
 * nav padre (móvil o TV).
 *
 * Se reutiliza en móvil y en TV (D-pad).
 */
@Composable
fun SettingsNavHost(
    onServers: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
    onSpeedTest: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SettingsRoutes.ROOT) {
        composable(SettingsRoutes.ROOT) {
            SettingsRootScreen(onGroup = { route -> navController.navigate(route) })
        }
        composable(SettingsRoutes.DATA) {
            DataScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.USERS) {
            UsersScreen(
                onBack = { navController.popBackStack() },
                onLogout = onLogout,
                onHome = onHome,
                onAddUser = onAddUser,
            )
        }
        composable(SettingsRoutes.PLAYLISTS) {
            PlaylistsScreen(
                onBack = { navController.popBackStack() },
                onServers = onServers,
            )
        }
        composable(SettingsRoutes.DOWNLOADS_PREFS) {
            DownloadsPrefsScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.NETWORK) {
            NetworkScreen(
                onBack = { navController.popBackStack() },
                onSpeedTest = onSpeedTest,
            )
        }
        composable(SettingsRoutes.PLAYBACK) {
            PlaybackScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.SUBTITLES) {
            SubtitlesScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.APPEARANCE) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
