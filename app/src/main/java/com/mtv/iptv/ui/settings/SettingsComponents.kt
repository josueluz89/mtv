package com.mtv.iptv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtv.iptv.ui.mobile.safeClickable
import kotlinx.coroutines.launch

/** Fila de ajuste con título + subtítulo, opcionalmente tocable. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var rowModifier = Modifier
        .fillMaxWidth()
        // En TV el foco debe VERSE y la lista debe desplazarse sola al
        // navegar con D-pad (verticalScroll no lo hace por sí solo).
        .onFocusChanged { focused = it.isFocused }
        .bringIntoViewRequester(bringIntoView)
        .onFocusEvent {
            if (it.isFocused) {
                scope.launch { bringIntoView.bringIntoView() }
            }
        }
        .background(
            if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else Color.Transparent,
            RoundedCornerShape(8.dp),
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)
    if (onClick != null) rowModifier = rowModifier.safeClickable(onClick = onClick)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Fila de ajuste con interruptor. */
@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** Diálogo genérico de opciones (radio buttons). */
@Composable
fun OptionsDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .safeClickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/** "hace 5 min", "hace 2 h", "ayer", etc. */
fun timeAgo(timestampMs: Long): String {
    if (timestampMs <= 0) return "nunca"
    val mins = (System.currentTimeMillis() - timestampMs) / 60_000
    return when {
        mins < 1 -> "ahora mismo"
        mins < 60 -> "hace $mins min"
        mins < 60 * 24 -> "hace ${mins / 60} h"
        else -> "hace ${mins / (60 * 24)} d"
    }
}
