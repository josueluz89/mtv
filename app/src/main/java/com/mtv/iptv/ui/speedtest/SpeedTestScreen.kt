package com.mtv.iptv.ui.speedtest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.local.db.SpeedTestRecord
import com.mtv.iptv.data.remote.SpeedTest
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ruta de navegación de la pantalla dedicada del test de velocidad. */
const val SPEEDTEST_ROUTE = "speedtest"

/**
 * Test de velocidad con vida propia: botón "Probar velocidad", resultado con
 * Mbps + veredicto (4K ≥25 / HD ≥8 / SD ≥3 / baja), línea de DNS, e historial
 * de mediciones persistido en Room (tabla speed_tests, BD mtv.db v2).
 *
 * La medición es una descarga cronometrada contra servidores de prueba
 * (CDN confiables, con reintentos y fallback): no depende del servidor Xtream
 * ni requiere sesión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val history by container.speedTestHistoryRepository.observeAll()
        .collectAsState(initial = emptyList())

    var running by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<SpeedTest.Result?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    fun runTest() = scope.launch {
        running = true
        try {
            val result = container.speedTest.run()
            lastResult = result
            container.speedTestHistoryRepository.record(
                mbps = result.mbps,
                dnsLabel = result.dnsLabel,
                verdict = result.verdict,
            )
            container.userPrefs.setLastSpeed(result.mbps.toFloat(), result.dnsLabel)
        } catch (e: Exception) {
            showMessage("No se pudo medir: ${e.message ?: "error de red"}")
        } finally {
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prueba de velocidad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                container.speedTestHistoryRepository.clear()
                                showMessage("Historial borrado")
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar historial")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Resultado / botón ----
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        running -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Midiendo velocidad…")
                        }
                        lastResult != null -> SpeedResultContent(lastResult!!)
                        else -> Text(
                            "Medí tu velocidad de internet con servidores de prueba.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { runTest() },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (running) "Midiendo…" else "Probar velocidad")
                    }
                }
            }

            // ---- Historial ----
            if (history.isNotEmpty()) {
                Text(
                    "Historial",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                history.forEach { record ->
                    HistoryRow(record)
                }
                Spacer(Modifier.height(24.dp))
            } else if (!running && lastResult == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Todavía no hay mediciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Resultado: Mbps grande + veredicto + línea de DNS. */
@Composable
private fun SpeedResultContent(result: SpeedTest.Result) {
    Text(
        "%.1f Mbps".format(result.mbps),
        style = MaterialTheme.typography.displaySmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(result.verdict, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        if (result.dnsLabel == "sistema") "DNS: sistema"
        else "DNS: ${result.dnsLabel} ✓",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

/** Fila del historial: Mbps, veredicto, fecha y DNS. */
@Composable
private fun HistoryRow(record: SpeedTestRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "%.1f Mbps".format(record.mbps),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    record.verdict,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    dateFormat.format(Date(record.measuredAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (record.dnsLabel == "sistema") "DNS: sistema"
                    else "DNS: ${record.dnsLabel} ✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
