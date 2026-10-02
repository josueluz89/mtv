package com.mtv.iptv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Listas de reproducción: gestión de los servidores Xtream guardados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(onBack: () -> Unit, onServers: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listas de reproducción") },
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
                SettingsRow(
                    title = "Servidores",
                    subtitle = "Gestionar servidores Xtream",
                    onClick = onServers,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
