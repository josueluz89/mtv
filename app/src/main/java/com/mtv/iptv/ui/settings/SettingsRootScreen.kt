package com.mtv.iptv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class SettingsGroup(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

/**
 * Raíz de Ajustes: lista de grupos. Cada grupo abre su pantalla secundaria.
 * Comparte la lógica v1.1, ahora organizada jerárquicamente.
 */
@Composable
fun SettingsRootScreen(onGroup: (String) -> Unit) {
    val groups = listOf(
        SettingsGroup(
            SettingsRoutes.DATA,
            "Datos y sincronización",
            "Caché y espacio usado",
            Icons.Default.Storage,
        ),
        SettingsGroup(
            SettingsRoutes.USERS,
            "Usuarios",
            "Cambiar de usuario, agregar o eliminar",
            Icons.Default.AccountCircle,
        ),
        SettingsGroup(
            SettingsRoutes.PLAYLISTS,
            "Listas de reproducción",
            "Servidores Xtream",
            Icons.Default.List,
        ),
        SettingsGroup(
            SettingsRoutes.DOWNLOADS_PREFS,
            "Descargas",
            "Calidad, Wi-Fi y espacio",
            Icons.Default.CloudDownload,
        ),
        SettingsGroup(
            SettingsRoutes.NETWORK,
            "Red",
            "DNS privado y prueba de velocidad",
            Icons.Default.Wifi,
        ),
        SettingsGroup(
            SettingsRoutes.PLAYBACK,
            "Reproducción",
            "Velocidad, autoplay, PiP",
            Icons.Default.PlayCircle,
        ),
        SettingsGroup(
            SettingsRoutes.APPEARANCE,
            "Apariencia",
            "Tema, idioma y orden del catálogo",
            Icons.Default.Palette,
        ),
        SettingsGroup(
            SettingsRoutes.ABOUT,
            "Acerca de",
            "Versión de la app",
            Icons.Default.Info,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        groups.forEach { group ->
            SettingsRow(
                title = group.title,
                subtitle = group.subtitle,
                onClick = { onGroup(group.route) },
                trailing = {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
