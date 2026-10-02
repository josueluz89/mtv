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

/** Reproducción: velocidad por defecto, autoplay, PiP y continuar donde quedó. */
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

    var speedDialog by remember { mutableStateOf(false) }

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
}
