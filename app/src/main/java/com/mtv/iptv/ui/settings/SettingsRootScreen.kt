package com.mtv.iptv.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class SettingsGroup(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tileColor: Color,
)

/**
 * Raíz de Ajustes: lista de grupos. Cada grupo abre su pantalla secundaria.
 * Comparte la lógica v1.1, ahora organizada jerárquicamente.
 * Orden estilo TiviMate: primero lo general, luego el contenido.
 *
 * En TV cada grupo lleva una baldosa de color sólido con el icono en blanco
 * (buen contraste sobre el fondo oscuro); en móvil se mantiene el icono
 * simple con el tinte primary de siempre.
 */
@Composable
fun SettingsRootScreen(onGroup: (String) -> Unit) {
    val context = LocalContext.current
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    val groups = listOf(
        SettingsGroup(
            SettingsRoutes.GENERAL,
            "General",
            "Arranque, PiP, User-Agent, respaldo",
            Icons.Default.Settings,
            Color(0xFFE02020),
        ),
        SettingsGroup(
            SettingsRoutes.PLAYLISTS,
            "Listas de reproducción",
            "Servidores Xtream",
            Icons.Default.List,
            Color(0xFF2E7DE9),
        ),
        SettingsGroup(
            SettingsRoutes.APPEARANCE,
            "Apariencia",
            "Tema, idioma y orden del catálogo",
            Icons.Default.Palette,
            Color(0xFF9C4DFF),
        ),
        SettingsGroup(
            SettingsRoutes.PLAYBACK,
            "Reproducción",
            "Velocidad, autoplay, PiP",
            Icons.Default.PlayCircle,
            Color(0xFF2ECC71),
        ),
        SettingsGroup(
            SettingsRoutes.REMOTE,
            "Mando a distancia",
            "Teclas de canal e info",
            Icons.Default.Gamepad,
            Color(0xFFFF8A00),
        ),
        SettingsGroup(
            SettingsRoutes.PARENTAL,
            "Control parental",
            "PIN y secciones bloqueadas",
            Icons.Default.Lock,
            Color(0xFFFFB300),
        ),
        SettingsGroup(
            SettingsRoutes.DATA,
            "Datos y sincronización",
            "Caché y espacio usado",
            Icons.Default.Storage,
            Color(0xFF00B8A9),
        ),
        SettingsGroup(
            SettingsRoutes.USERS,
            "Usuarios",
            "Cambiar de usuario, agregar o eliminar",
            Icons.Default.AccountCircle,
            Color(0xFF29B6F6),
        ),
        SettingsGroup(
            SettingsRoutes.DOWNLOADS_PREFS,
            "Descargas",
            "Calidad, Wi-Fi y espacio",
            Icons.Default.CloudDownload,
            Color(0xFF5C6BC0),
        ),
        SettingsGroup(
            SettingsRoutes.NETWORK,
            "Red",
            "DNS privado y prueba de velocidad",
            Icons.Default.Wifi,
            Color(0xFF9CCC65),
        ),
        SettingsGroup(
            SettingsRoutes.SUBTITLES,
            "Subtítulos",
            "Tamaño, fondo y color",
            Icons.Default.ClosedCaption,
            Color(0xFFEC407A),
        ),
        SettingsGroup(
            SettingsRoutes.ABOUT,
            "Acerca de",
            "Versión de la app",
            Icons.Default.Info,
            Color(0xFF78909C),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        groups.forEach { group ->
            SettingsGroupRow(
                group = group,
                isTv = isTv,
                onGroup = onGroup,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Una fila de grupo de ajustes.
 *
 * En TV la fila se envuelve en un contenedor que observa `hasFocus` (mismo
 * patrón que TvIconRail): así la baldosa sabe cuándo la fila tiene el foco
 * del D-pad y dibuja su borde blanco, sin crear focos anidados dentro de la
 * fila ni tocar [SettingsRow]. En móvil el contenedor es transparente y el
 * aspecto queda exactamente igual que antes.
 */
@Composable
private fun SettingsGroupRow(
    group: SettingsGroup,
    isTv: Boolean,
    onGroup: (String) -> Unit,
) {
    var rowFocused by remember { mutableStateOf(false) }
    Box(
        modifier = if (isTv) {
            Modifier.onFocusChanged { rowFocused = it.hasFocus }
        } else {
            Modifier
        },
    ) {
        SettingsRow(
            title = group.title,
            subtitle = group.subtitle,
            onClick = { onGroup(group.route) },
            leading = {
                if (isTv) {
                    SettingsTile(group = group, focused = rowFocused)
                } else {
                    Icon(
                        group.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            trailing = {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/** Baldosa de color sólido con el icono en blanco, solo TV. */
@Composable
private fun SettingsTile(group: SettingsGroup, focused: Boolean) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(group.tileColor)
            .then(
                if (focused) {
                    Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = group.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}
