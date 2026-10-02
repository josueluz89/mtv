package com.mtv.iptv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.SpeedTest
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Red: DNS privado (Cloudflare 1.1.1.1 por DoH), DNS activo y prueba de
 * velocidad (pantalla dedicada en la ruta "speedtest").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit, onSpeedTest: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()

    val privateDns by prefs.privateDns.collectAsState(initial = false)
    val lastSpeedMbps by prefs.lastSpeedMbps.collectAsState(initial = -1f)
    val lastSpeedAt by prefs.lastSpeedAt.collectAsState(initial = 0L)

    fun lastSpeedSubtitle(): String {
        if (lastSpeedMbps < 0) return "Medí tu conexión contra el servidor"
        return "Última: %.1f Mbps · %s · %s".format(
            lastSpeedMbps, SpeedTest.verdictFor(lastSpeedMbps.toDouble()), timeAgo(lastSpeedAt)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Red") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsSwitch(
                    title = "DNS privado (Cloudflare 1.1.1.1)",
                    subtitle = "Evita bloqueos de tu proveedor de internet",
                    checked = privateDns,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            prefs.setPrivateDns(enabled)
                            // Aplica de inmediato al proveedor HTTP (además de persistir).
                            container.httpClientProvider.usePrivateDns = enabled
                        }
                    },
                )
                SettingsRow(
                    title = "DNS activo",
                    subtitle = if (privateDns) "Cloudflare 1.1.1.1" else "DNS del sistema",
                    onClick = null,
                )
                SettingsRow(
                    title = "Probar velocidad",
                    subtitle = lastSpeedSubtitle(),
                    onClick = onSpeedTest,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
