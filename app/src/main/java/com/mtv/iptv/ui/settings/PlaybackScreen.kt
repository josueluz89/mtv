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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(speed: Float): String = "${speedOptions.firstOrNull { it == speed } ?: speed}x"

private val playerModeOptions = listOf("auto", "internal", "external")

private fun playerModeLabel(mode: String): String = when (mode) {
    "internal" -> "Interno (app)"
    "external" -> "Externo (VLC)"
    else -> "Automático"
}

private fun playerModeDescription(mode: String): String = when (mode) {
    "internal" -> "Siempre usa el reproductor de la app"
    "external" -> "Siempre usa VLC externo"
    else -> "Usa VLC externo si está instalado, si no el interno"
}

/** Reproducción: velocidad por defecto, autoplay, PiP, continuar donde quedó y reproductor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()

    val defaultSpeed by prefs.defaultSpeed.collectAsState(initial = 1f)
    val autoplayNext by prefs.autoplayNext.collectAsState(initial = false)
    val pipEnabled by prefs.pipEnabled.collectAsState(initial = true)
    val resumeEnabled by prefs.resumeEnabled.collectAsState(initial = true)
    val playerMode by prefs.playerMode.collectAsState(initial = "auto")

    var speedDialog by remember { mutableStateOf(false) }
    var playerDialog by remember { mutableStateOf(false) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproducción") },
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
                    title = "Velocidad por defecto",
                    subtitle = speedLabel(defaultSpeed),
                    onClick = { speedDialog = true },
                )
                SettingsSwitch(
                    title = "Siguiente episodio automático",
                    subtitle = "Reproduce el próximo episodio al terminar uno",
                    checked = autoplayNext,
                    onCheckedChange = { setPref { prefs.setAutoplayNext(it) } },
                )
                SettingsSwitch(
                    title = "Picture-in-Picture",
                    subtitle = "Muestra el botón de ventana flotante en el reproductor",
                    checked = pipEnabled,
                    onCheckedChange = { setPref { prefs.setPipEnabled(it) } },
                )
                SettingsSwitch(
                    title = "Continuar donde quedó",
                    subtitle = "Pregunta si retomar la reproducción al abrir un video",
                    checked = resumeEnabled,
                    onCheckedChange = { setPref { prefs.setResumeEnabled(it) } },
                )
                SettingsRow(
                    title = "Reproductor",
                    subtitle = "${playerModeLabel(playerMode)} · ${playerModeDescription(playerMode)}",
                    onClick = { playerDialog = true },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (speedDialog) {
        OptionsDialog(
            title = "Velocidad por defecto",
            options = speedOptions.map { "$it" to "${it}x" },
            selected = "$defaultSpeed",
            onSelect = {
                setPref { prefs.setDefaultSpeed(it.toFloatOrNull() ?: 1f) }
                speedDialog = false
            },
            onDismiss = { speedDialog = false },
        )
    }

    if (playerDialog) {
        OptionsDialog(
            title = "Reproductor",
            options = playerModeOptions.map { it to playerModeLabel(it) },
            selected = playerMode,
            onSelect = {
                setPref { prefs.setPlayerMode(it) }
                playerDialog = false
            },
            onDismiss = { playerDialog = false },
        )
    }
}
