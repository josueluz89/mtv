package com.mtv.iptv.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mtv.iptv.data.local.prefs.UserPrefs
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.flow.first

/**
 * Indica si una sección está bloqueada con PIN de control parental.
 *
 * @param section "tv" | "movies" | "series".
 * @return true si hay un PIN configurado y el bloqueo de esa sección está activo.
 */
suspend fun isSectionLocked(prefs: UserPrefs, section: String): Boolean {
    if (prefs.parentalPin.first().isEmpty()) return false
    return when (section) {
        "tv" -> prefs.lockTv.first()
        "movies" -> prefs.lockMovies.first()
        "series" -> prefs.lockSeries.first()
        else -> false
    }
}

/**
 * Teclado PIN operable con D-pad (sin teclado táctil).
 *
 * 4 casillas: Arriba/Abajo cambia el dígito, Izquierda/Derecha mueve el
 * cursor, OK (centro/enter) confirma, teclas numéricas escriben directo,
 * Atrás cancela.
 *
 * @param mask si es true muestra puntos en vez de los dígitos.
 */
@Composable
fun PinPadInput(
    title: String,
    subtitle: String = "",
    mask: Boolean = false,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var digits by remember { mutableStateOf(IntArray(4)) }
    var cursor by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    fun handleKey(keyCode: Int): Boolean {
        when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                val next = digits.copyOf()
                next[cursor] = (next[cursor] + 1) % 10
                digits = next
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                val next = digits.copyOf()
                next[cursor] = (next[cursor] + 9) % 10
                digits = next
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                cursor = (cursor + 3) % 4
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursor = (cursor + 1) % 4
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                onComplete(digits.joinToString(""))
                return true
            }
            AndroidKeyEvent.KEYCODE_BACK -> {
                onCancel()
                return true
            }
            else -> {
                if (keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9) {
                    val next = digits.copyOf()
                    next[cursor] = keyCode - AndroidKeyEvent.KEYCODE_0
                    digits = next
                    cursor = (cursor + 1) % 4
                    return true
                }
                return false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                if (it.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                    handleKey(it.nativeKeyEvent.keyCode)
                } else {
                    false
                }
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            digits.forEachIndexed { index, digit ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (index == cursor) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (mask) "•" else "$digit",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index == cursor) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Arriba/Abajo: dígito · Izq/Der: mover · OK: confirmar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/**
 * Diálogo que pide el PIN parental con el pad D-pad. Si el PIN coincide
 * llama a [onUnlocked]; si no, muestra error y deja reintentar.
 */
@Composable
fun ParentalPinDialog(
    prefs: UserPrefs,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pin by prefs.parentalPin.collectAsState(initial = "")
    var wrong by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
        ) {
            PinPadInput(
                title = "Control parental",
                subtitle = if (wrong) "PIN incorrecto, inténtalo de nuevo"
                else "Escribe el PIN para desbloquear",
                mask = true,
                onComplete = { entered ->
                    if (entered == pin && pin.isNotEmpty()) {
                        wrong = false
                        onUnlocked()
                    } else {
                        wrong = true
                    }
                },
                onCancel = onDismiss,
            )
        }
    }
}

/** Variante que toma el contenedor de la app en vez del UserPrefs directo. */
@Composable
fun ParentalPinDialog(
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    ParentalPinDialog(
        prefs = LocalAppContainer.current.userPrefs,
        onUnlocked = onUnlocked,
        onDismiss = onDismiss,
    )
}
