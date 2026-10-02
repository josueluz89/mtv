package com.mtv.iptv.ui.settings

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

private fun keyLabel(code: Int): String =
    AndroidKeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

/** Mando a distancia: asigna las teclas de canal, guía e info del programa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()

    val keyChannelUp by prefs.keyChannelUp.collectAsState(initial = AndroidKeyEvent.KEYCODE_CHANNEL_UP)
    val keyChannelDown by prefs.keyChannelDown.collectAsState(initial = AndroidKeyEvent.KEYCODE_CHANNEL_DOWN)
    val keyInfo by prefs.keyInfo.collectAsState(initial = AndroidKeyEvent.KEYCODE_INFO)

    var capturing by remember { mutableIntStateOf(-1) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    val rows = listOf(
        Triple("Canal siguiente", keyChannelUp, { code: Int -> setPref { prefs.setKeyChannelUp(code) } }),
        Triple("Canal anterior", keyChannelDown, { code: Int -> setPref { prefs.setKeyChannelDown(code) } }),
        Triple("Info del programa", keyInfo, { code: Int -> setPref { prefs.setKeyInfo(code) } }),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mando a distancia") },
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
                rows.forEachIndexed { index, (title, code, save) ->
                    SettingsRow(
                        title = title,
                        subtitle = keyLabel(code),
                        onClick = { capturing = index },
                    )
                }
                SettingsRow(
                    title = "Restablecer teclas",
                    subtitle = "Volver a los valores por defecto",
                    onClick = {
                        setPref {
                            prefs.setKeyChannelUp(AndroidKeyEvent.KEYCODE_CHANNEL_UP)
                            prefs.setKeyChannelDown(AndroidKeyEvent.KEYCODE_CHANNEL_DOWN)
                            prefs.setKeyInfo(AndroidKeyEvent.KEYCODE_INFO)
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (capturing >= 0) {
        val (title, _, save) = rows[capturing]
        val focusRequester = remember { FocusRequester() }

        Dialog(onDismissRequest = { capturing = -1 }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent {
                            val native = it.nativeKeyEvent
                            if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                                val code = native.keyCode
                                if (code == AndroidKeyEvent.KEYCODE_BACK) {
                                    capturing = -1
                                } else {
                                    save(code)
                                    capturing = -1
                                }
                                true
                            } else {
                                false
                            }
                        }
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Pulsa una tecla…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        LaunchedEffect(capturing) { focusRequester.requestFocus() }
    }
}
