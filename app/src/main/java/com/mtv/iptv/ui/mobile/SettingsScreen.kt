package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mtv.iptv.ui.settings.SettingsNavHost

/**
 * Contenido de configuración reutilizable (móvil y TV).
 *
 * Ahora es jerárquico: la raíz lista los grupos (Datos y sincronización,
 * Usuarios, Listas de reproducción, Descargas, Red, Reproducción,
 * Apariencia, Acerca de) y cada grupo abre su pantalla secundaria.
 * La implementación vive en ui/settings ([SettingsNavHost]).
 */
@Composable
fun SettingsContent(
    onServers: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
    onSpeedTest: () -> Unit = {},
) {
    SettingsNavHost(
        onServers = onServers,
        onLogout = onLogout,
        onHome = onHome,
        onAddUser = onAddUser,
        onSpeedTest = onSpeedTest,
    )
}

/** Pantalla de configuración (móvil). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onServers: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
    onSpeedTest: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SettingsContent(
                onServers = onServers,
                onLogout = onLogout,
                onHome = onHome,
                onAddUser = onAddUser,
                onSpeedTest = onSpeedTest,
            )
        }
    }
}
