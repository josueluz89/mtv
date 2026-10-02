package com.mtv.iptv.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mtv.iptv.di.AppContainer
import java.io.File

/** Indicador flotante de gestos (volumen / brillo / seek), compartido móvil+TV. */
internal data class PlayerIndicator(val icon: ImageVector, val text: String)

@Composable
internal fun PlayerIndicatorCard(ind: PlayerIndicator?) {
    ind?.let {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(it.icon, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(it.text, color = Color.White)
            }
        }
    }
}

/**
 * Resuelve la URL a reproducir con libVLC: si el item está descargado
 * (caché de descargas de Media3), devuelve el `file://` del archivo local;
 * si no, la URL remota tal cual. libVLC no comparte el caché de ExoPlayer,
 * así que hay que apuntarlo al archivo físico.
 */
internal fun AppContainer.resolvePlaybackUrl(url: String): String {
    val local: File? = try {
        downloadModule.localPlaybackFile(url)
    } catch (_: Exception) {
        null
    }
    return if (local != null) "file://${local.absolutePath}" else url
}
