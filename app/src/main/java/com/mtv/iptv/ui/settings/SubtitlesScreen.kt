package com.mtv.iptv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

private val sizeOptions = listOf("S", "M", "L")

private fun sizeLabel(size: String) = when (size) {
    "S" -> "Pequeño"
    "L" -> "Grande"
    else -> "Mediano"
}

private val backgroundOptions = listOf("solido", "semi", "ninguno")

private fun backgroundLabel(value: String) = when (value) {
    "solido" -> "Sólido (recuadro negro)"
    "ninguno" -> "Sin fondo (solo sombra)"
    else -> "Semitransparente"
}

private val colorOptions = listOf("blanco", "amarillo", "cian", "verde")

private fun colorLabel(value: String) = when (value) {
    "amarillo" -> "Amarillo"
    "cian" -> "Cian"
    "verde" -> "Verde"
    else -> "Blanco"
}

private fun previewColor(value: String): Color = when (value) {
    "amarillo" -> Color.Yellow
    "cian" -> Color.Cyan
    "verde" -> Color.Green
    else -> Color.White
}

private fun previewFontSize(size: String) = when (size) {
    "S" -> 14.sp
    "L" -> 24.sp
    else -> 18.sp
}

/**
 * Subtítulos: tamaño, fondo del recuadro y color del texto, con vista previa.
 * Se aplica al reproductor interno (ExoPlayer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()

    val subtitleSize by prefs.subtitleSize.collectAsState(initial = "M")
    val subtitleBackground by prefs.subtitleBackground.collectAsState(initial = "semi")
    val subtitleColor by prefs.subtitleColor.collectAsState(initial = "blanco")

    var sizeDialog by remember { mutableStateOf(false) }
    var backgroundDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subtítulos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Vista previa con el estilo elegido.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Así se verán los subtítulos",
                    color = previewColor(subtitleColor),
                    style = TextStyle(
                        fontSize = previewFontSize(subtitleSize),
                        shadow = if (subtitleBackground == "ninguno") {
                            Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 6f,
                            )
                        } else {
                            null
                        },
                    ),
                    modifier = when (subtitleBackground) {
                        "solido" -> Modifier.background(Color.Black)
                        "ninguno" -> Modifier
                        else -> Modifier.background(Color.Black.copy(alpha = 0.6f))
                    }.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            SettingsRow(
                title = "Tamaño del texto",
                subtitle = sizeLabel(subtitleSize),
                onClick = { sizeDialog = true },
            )
            SettingsRow(
                title = "Fondo",
                subtitle = backgroundLabel(subtitleBackground),
                onClick = { backgroundDialog = true },
            )
            SettingsRow(
                title = "Color del texto",
                subtitle = colorLabel(subtitleColor),
                onClick = { colorDialog = true },
            )
            Text(
                "Se aplica al reproductor interno de la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (sizeDialog) {
        OptionsDialog(
            title = "Tamaño del texto",
            options = sizeOptions.map { it to sizeLabel(it) },
            selected = subtitleSize,
            onSelect = {
                setPref { prefs.setSubtitleSize(it) }
                sizeDialog = false
            },
            onDismiss = { sizeDialog = false },
        )
    }
    if (backgroundDialog) {
        OptionsDialog(
            title = "Fondo",
            options = backgroundOptions.map { it to backgroundLabel(it) },
            selected = subtitleBackground,
            onSelect = {
                setPref { prefs.setSubtitleBackground(it) }
                backgroundDialog = false
            },
            onDismiss = { backgroundDialog = false },
        )
    }
    if (colorDialog) {
        OptionsDialog(
            title = "Color del texto",
            options = colorOptions.map { it to colorLabel(it) },
            selected = subtitleColor,
            onSelect = {
                setPref { prefs.setSubtitleColor(it) }
                colorDialog = false
            },
            onDismiss = { colorDialog = false },
        )
    }
}
