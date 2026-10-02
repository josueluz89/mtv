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
import com.mtv.iptv.BuildConfig

/** Acerca de: versión de la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
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
                    title = "Versión",
                    subtitle = BuildConfig.VERSION_NAME,
                    onClick = null,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Decodificador de audio FFmpeg",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Esta app incluye FFmpeg compilado bajo la licencia LGPL 2.1+ " +
                        "(--disable-gpl --disable-nonfree), usado solo como " +
                        "decodificador de audio de respaldo. Los scripts exactos " +
                        "de compilación están publicados en el repositorio del " +
                        "proyecto (módulo decoder_ffmpeg).",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Versión FFmpeg: " + ffmpegVersion(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Versión del FFmpeg compilado, o "no incluido" si el respaldo no se empaquetó. */
private fun ffmpegVersion(): String {
    return try {
        androidx.media3.decoder.ffmpeg.FfmpegLibrary.getVersion().orEmpty()
            .ifBlank { "no incluido en esta compilación" }
    } catch (_: Throwable) {
        "no incluido en esta compilación"
    }
}
