package com.mtv.iptv.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.tv.PinPadInput
import kotlinx.coroutines.launch

private enum class PinFlow {
    NONE, CREATE, CONFIRM_NEW, VERIFY_CHANGE, VERIFY_REMOVE,
}

/** Control parental: PIN de 4 dígitos (pad D-pad) y secciones bloqueadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pin by prefs.parentalPin.collectAsState(initial = "")
    val lockTv by prefs.lockTv.collectAsState(initial = false)
    val lockMovies by prefs.lockMovies.collectAsState(initial = false)
    val lockSeries by prefs.lockSeries.collectAsState(initial = false)

    var flow by remember { mutableStateOf(PinFlow.NONE) }
    var firstPin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control parental") },
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
                if (pin.isEmpty()) {
                    SettingsRow(
                        title = "Crear PIN",
                        subtitle = "Protege secciones con un PIN de 4 dígitos",
                        onClick = {
                            firstPin = ""
                            wrong = false
                            mismatch = false
                            flow = PinFlow.CREATE
                        },
                    )
                } else {
                    SettingsRow(
                        title = "Cambiar PIN",
                        subtitle = "Pide el PIN actual y luego el nuevo",
                        onClick = {
                            wrong = false
                            flow = PinFlow.VERIFY_CHANGE
                        },
                    )
                    SettingsRow(
                        title = "Quitar PIN",
                        subtitle = "Desactiva el control parental",
                        onClick = {
                            wrong = false
                            flow = PinFlow.VERIFY_REMOVE
                        },
                    )
                    SettingsSwitch(
                        title = "Bloquear TV en vivo",
                        subtitle = "Pide el PIN para entrar a TV en vivo",
                        checked = lockTv,
                        onCheckedChange = { setPref { prefs.setLockTv(it) } },
                    )
                    SettingsSwitch(
                        title = "Bloquear Películas",
                        subtitle = "Pide el PIN para entrar a Películas",
                        checked = lockMovies,
                        onCheckedChange = { setPref { prefs.setLockMovies(it) } },
                    )
                    SettingsSwitch(
                        title = "Bloquear Shows",
                        subtitle = "Pide el PIN para entrar a Shows",
                        checked = lockSeries,
                        onCheckedChange = { setPref { prefs.setLockSeries(it) } },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (flow != PinFlow.NONE) {
        Dialog(onDismissRequest = { flow = PinFlow.NONE }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
            ) {
                when (flow) {
                    PinFlow.CREATE -> PinPadInput(
                        title = "Crear PIN",
                        subtitle = if (mismatch) "No coinciden, inténtalo de nuevo"
                        else "Elige un PIN de 4 dígitos",
                        onComplete = {
                            firstPin = it
                            mismatch = false
                            flow = PinFlow.CONFIRM_NEW
                        },
                        onCancel = { flow = PinFlow.NONE },
                    )
                    PinFlow.CONFIRM_NEW -> PinPadInput(
                        title = "Confirma el PIN",
                        onComplete = {
                            if (it == firstPin) {
                                setPref { prefs.setParentalPin(it) }
                                flow = PinFlow.NONE
                                toast("PIN creado")
                            } else {
                                mismatch = true
                                firstPin = ""
                                flow = PinFlow.CREATE
                            }
                        },
                        onCancel = { flow = PinFlow.NONE },
                    )
                    PinFlow.VERIFY_CHANGE -> PinPadInput(
                        title = "PIN actual",
                        subtitle = if (wrong) "PIN incorrecto, inténtalo de nuevo" else "",
                        mask = true,
                        onComplete = {
                            if (it == pin && pin.isNotEmpty()) {
                                wrong = false
                                firstPin = ""
                                mismatch = false
                                flow = PinFlow.CREATE
                            } else {
                                wrong = true
                            }
                        },
                        onCancel = { flow = PinFlow.NONE },
                    )
                    PinFlow.VERIFY_REMOVE -> PinPadInput(
                        title = "Quitar PIN",
                        subtitle = if (wrong) "PIN incorrecto, inténtalo de nuevo"
                        else "Escribe el PIN actual para quitarlo",
                        mask = true,
                        onComplete = {
                            if (it == pin && pin.isNotEmpty()) {
                                setPref { prefs.setParentalPin("") }
                                flow = PinFlow.NONE
                                toast("PIN eliminado")
                            } else {
                                wrong = true
                            }
                        },
                        onCancel = { flow = PinFlow.NONE },
                    )
                    PinFlow.NONE -> Unit
                }
            }
        }
    }
}
