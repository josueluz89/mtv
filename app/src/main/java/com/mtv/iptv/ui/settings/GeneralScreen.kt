package com.mtv.iptv.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * General: arranque, último canal, PiP al ir a Home, confirmación de
 * salida, User-Agent, respaldo y restablecimiento de datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val openOnBoot by prefs.openOnBoot.collectAsState(initial = false)
    val openLastChannel by prefs.openLastChannel.collectAsState(initial = false)
    val pipOnHome by prefs.pipOnHome.collectAsState(initial = false)
    val confirmExit by prefs.confirmExit.collectAsState(initial = true)
    val userAgent by prefs.userAgent.collectAsState(initial = "")

    var uaDialog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    fun backupData() {
        scope.launch(Dispatchers.IO) {
            val message = try {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val dir = File(context.getExternalFilesDir("backup"), "mtv-backup-$stamp")
                    .apply { mkdirs() }
                // DB Room ("mtv.db" en MtvDatabase) con sus archivos WAL.
                val dbDir = context.getDatabasePath("mtv.db").parentFile
                var copied = 0
                listOf("mtv.db", "mtv.db-wal", "mtv.db-shm").forEach { name ->
                    val src = dbDir?.let { File(it, name) }
                    if (src != null && src.exists()) {
                        src.copyTo(File(dir, name), overwrite = true)
                        copied++
                    }
                }
                // DataStore de ajustes.
                val prefsFile = File(context.filesDir, "datastore/mtv_prefs.preferences_pb")
                if (prefsFile.exists()) {
                    prefsFile.copyTo(File(dir, prefsFile.name), overwrite = true)
                    copied++
                }
                if (copied == 0) "No hay datos que respaldar"
                else "Respaldo guardado en ${dir.name}"
            } catch (e: Exception) {
                "No se pudo respaldar: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun resetData() {
        scope.launch(Dispatchers.IO) {
            val message = try {
                prefs.clearAll()
                val db = container.database
                db.serverDao().clear()
                db.favoriteDao().clear()
                db.playbackDao().clear()
                db.speedTestDao().clear()
                "Reinicia la app"
            } catch (e: Exception) {
                "No se pudo restablecer: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("General") },
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
                SettingsSwitch(
                    title = "Abrir la app al encender",
                    subtitle = "Inicia MTV automáticamente al encender el dispositivo",
                    checked = openOnBoot,
                    onCheckedChange = { setPref { prefs.setOpenOnBoot(it) } },
                )
                SettingsSwitch(
                    title = "Abrir el último canal al abrir la app",
                    subtitle = "Reproduce el último canal de TV en vivo al iniciar",
                    checked = openLastChannel,
                    onCheckedChange = { setPref { prefs.setOpenLastChannel(it) } },
                )
                SettingsSwitch(
                    title = "Picture-in-picture al pulsar Home",
                    subtitle = "Pasa a ventana flotante en vez de pausar al ir a Home",
                    checked = pipOnHome,
                    onCheckedChange = { setPref { prefs.setPipOnHome(it) } },
                )
                SettingsSwitch(
                    title = "Confirmar al salir (pulsar Atrás dos veces)",
                    subtitle = "Pide pulsar Atrás dos veces para cerrar la app",
                    checked = confirmExit,
                    onCheckedChange = { setPref { prefs.setConfirmExit(it) } },
                )
                SettingsRow(
                    title = "User-Agent",
                    subtitle = userAgent.ifEmpty { "El de la app" },
                    onClick = { uaDialog = true },
                )
                SettingsRow(
                    title = "Respaldar datos",
                    subtitle = "Copia la base de datos y los ajustes a una carpeta",
                    onClick = ::backupData,
                )
                SettingsRow(
                    title = "Restablecer datos",
                    subtitle = "Borra favoritos, historial y ajustes",
                    onClick = { confirmReset = true },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (uaDialog) {
        var value by remember { mutableStateOf(userAgent) }
        AlertDialog(
            onDismissRequest = { uaDialog = false },
            title = { Text("User-Agent") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Vacío = el de la app") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    setPref { prefs.setUserAgent(value.trim()) }
                    uaDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { uaDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restablecer datos") },
            text = { Text("Borra favoritos, historial y ajustes. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    resetData()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar") }
            },
        )
    }
}
