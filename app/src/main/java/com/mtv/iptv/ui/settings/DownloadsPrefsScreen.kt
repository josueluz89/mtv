package com.mtv.iptv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.scheduler.Requirements
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.downloads.calcUsedSpace
import com.mtv.iptv.ui.downloads.formatBytes
import com.mtv.iptv.ui.downloads.removeAllDownloads
import kotlinx.coroutines.launch

private val qualityOptions = listOf(
    "auto" to "Automática",
    "alta" to "Alta",
    "media" to "Media",
    "baja" to "Baja",
)

/** Descargas: calidad, solo Wi-Fi, espacio usado y borrado total. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsPrefsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()
    val downloadManager = container.downloadModule.downloadManager

    val dlQuality by prefs.dlQuality.collectAsState(initial = "alta")
    val dlWifiOnly by prefs.dlWifiOnly.collectAsState(initial = true)

    var qualityDialog by remember { mutableStateOf(false) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    var usedBytes by remember { mutableStateOf(-1L) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }
    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }
    fun refreshUsedSpace() { usedBytes = calcUsedSpace(downloadManager) }

    fun deleteAllDownloads() = scope.launch {
        val n = removeAllDownloads(downloadManager)
        refreshUsedSpace()
        showMessage(if (n == 0) "No hay descargas" else "Descargas eliminadas")
    }

    LaunchedEffect(Unit) { refreshUsedSpace() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsRow(
                    title = "Calidad de descarga",
                    subtitle = qualityOptions.firstOrNull { it.first == dlQuality }?.second ?: dlQuality,
                    onClick = { qualityDialog = true },
                )
                SettingsSwitch(
                    title = "Solo con Wi-Fi",
                    subtitle = "Descargar solo con conexiones sin medidor",
                    checked = dlWifiOnly,
                    onCheckedChange = { enabled ->
                        setPref {
                            prefs.setDlWifiOnly(enabled)
                            downloadManager.setRequirements(
                                Requirements(
                                    if (enabled) Requirements.NETWORK_UNMETERED
                                    else Requirements.NETWORK
                                )
                            )
                        }
                    },
                )
                SettingsRow(
                    title = "Espacio usado",
                    subtitle = formatBytes(usedBytes),
                    onClick = null,
                )
                Button(
                    onClick = { confirmDeleteDownloads = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Borrar todas las descargas") }
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    if (qualityDialog) {
        OptionsDialog(
            title = "Calidad de descarga",
            options = qualityOptions,
            selected = dlQuality,
            onSelect = {
                setPref { prefs.setDlQuality(it) }
                qualityDialog = false
            },
            onDismiss = { qualityDialog = false },
        )
    }
    if (confirmDeleteDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDownloads = false },
            title = { Text("Borrar descargas") },
            text = { Text("Se eliminan todas las descargas guardadas. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteDownloads = false; deleteAllDownloads() }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDownloads = false }) { Text("Cancelar") }
            },
        )
    }
}
