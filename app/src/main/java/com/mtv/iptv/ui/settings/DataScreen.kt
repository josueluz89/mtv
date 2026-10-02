package com.mtv.iptv.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.downloads.calcUsedSpace
import com.mtv.iptv.ui.downloads.formatBytes
import com.mtv.iptv.ui.downloads.removeAllDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Datos y sincronización: caché de imágenes/temporales y espacio de descargas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloadManager = container.downloadModule.downloadManager

    var usedBytes by remember { mutableStateOf(-1L) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Actualización automática del catálogo.
    val prefs = container.userPrefs
    val catalogFreq by prefs.catalogFreq.collectAsState(initial = "12h")
    val catalogBackground by prefs.catalogBackground.collectAsState(initial = true)
    var freqDialog by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf(0L) }

    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    fun refreshUsedSpace() {
        usedBytes = calcUsedSpace(downloadManager)
    }

    fun rescheduleWorker() {
        (context.applicationContext as? com.mtv.iptv.MtvApplication)?.rescheduleCatalogWorker()
    }

    fun refreshNow() = scope.launch {
        refreshing = true
        val ok = try {
            container.xtreamRepository.forceRefreshCatalog()
        } catch (_: Exception) {
            false
        }
        if (ok) {
            val now = System.currentTimeMillis()
            try {
                prefs.setLastCatalogRefresh(now)
            } catch (_: Exception) {
            }
            container.catalogRefreshTick.value = now
            lastRefresh = now
            showMessage("Catálogo actualizado")
        } else {
            showMessage("No se pudo actualizar (revisá la sesión y la red)")
        }
        refreshing = false
    }

    LaunchedEffect(Unit) {
        refreshUsedSpace()
        lastRefresh = try {
            prefs.getLastCatalogRefresh()
        } catch (_: Exception) {
            0L
        }
    }

    fun clearCache() = scope.launch {
        withContext(Dispatchers.IO) {
            try {
                context.imageLoader.diskCache?.clear()
            } catch (_: Exception) {
            }
            clearTempCache(context)
        }
        showMessage("Caché limpiado")
    }

    fun deleteAllDownloads() = scope.launch {
        val n = removeAllDownloads(downloadManager)
        refreshUsedSpace()
        showMessage(if (n == 0) "No hay descargas" else "Descargas eliminadas")
    }

    LaunchedEffect(Unit) { refreshUsedSpace() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datos y sincronización") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsRow(
                    title = "Espacio usado por descargas",
                    subtitle = formatBytes(usedBytes),
                    onClick = null,
                )
                SettingsRow(
                    title = "Actualizar catálogo ahora",
                    subtitle = if (refreshing) "Actualizando…" else "Última actualización: ${timeAgo(lastRefresh)}",
                    onClick = { if (!refreshing) refreshNow() },
                )
                SettingsRow(
                    title = "Frecuencia de actualización",
                    subtitle = com.mtv.iptv.work.CatalogWork.freqLabel(catalogFreq),
                    onClick = { freqDialog = true },
                )
                SettingsSwitch(
                    title = "Actualización en segundo plano",
                    subtitle = if (catalogBackground) "El catálogo se actualiza solo" else "Apagada",
                    checked = catalogBackground,
                    onCheckedChange = { v ->
                        scope.launch {
                            prefs.setCatalogBackground(v)
                            rescheduleWorker()
                        }
                    },
                )
                Button(
                    onClick = { confirmClearCache = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Limpiar caché") }
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

    if (freqDialog) {
        OptionsDialog(
            title = "Frecuencia de actualización",
            options = listOf("6h", "12h", "24h", "manual").map { it to com.mtv.iptv.work.CatalogWork.freqLabel(it) },
            selected = catalogFreq,
            onSelect = { v ->
                scope.launch {
                    prefs.setCatalogFreq(v)
                    rescheduleWorker()
                }
                freqDialog = false
            },
            onDismiss = { freqDialog = false },
        )
    }
    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Limpiar caché") },
            text = { Text("Se borran las imágenes en caché y los archivos temporales. Las descargas no se tocan.") },
            confirmButton = {
                TextButton(onClick = { confirmClearCache = false; clearCache() }) { Text("Limpiar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Cancelar") }
            },
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

/**
 * Borra temporales del cacheDir, EXCLUYENDO el directorio de descargas.
 * Se excluyen "mtv-downloads" (caché real de Media3 en DownloadModule) y
 * "downloads" (nombre típico del caché de Media3).
 */
fun clearTempCache(context: Context) {
    fun clean(file: File) {
        if (file.isDirectory) {
            if (file.name == "downloads" || file.name == "mtv-downloads") return
            file.listFiles()?.forEach { clean(it) }
            file.delete()
        } else {
            file.delete()
        }
    }
    context.cacheDir.listFiles()?.forEach { clean(it) }
}
