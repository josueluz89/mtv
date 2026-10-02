package com.mtv.iptv.ui.common

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mtv.iptv.di.LocalAppContainer

/**
 * Confirmación de salida con doble Atrás (solo TV).
 *
 * Si [enabled] es true y el ajuste "Confirmar al salir" está activo: el
 * primer Atrás muestra "Pulsa Atrás otra vez para salir" y el segundo
 * (antes de 2 s) cierra la activity. En móvil el nav ya maneja el atrás,
 * así que el riel/agente TV debe pasar enabled=false ahí.
 */
@Composable
fun DoubleBackToExit(enabled: Boolean) {
    val context = LocalContext.current
    val prefs = LocalAppContainer.current.userPrefs
    val confirmExit by prefs.confirmExit.collectAsState(initial = true)
    var lastPress by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = enabled && confirmExit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPress < 2_000) {
            (context as? Activity)?.finish()
        } else {
            lastPress = now
            Toast.makeText(context, "Pulsa Atrás otra vez para salir", Toast.LENGTH_SHORT).show()
        }
    }
}
