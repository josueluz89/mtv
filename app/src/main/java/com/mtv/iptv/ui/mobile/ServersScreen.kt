package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.launch

/**
 * Login simplificado: solo usuario y contraseña. Los servidores (DNS) están
 * ocultos en el código y se prueban en orden hasta que uno acepta las
 * credenciales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(onConnected: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        username = container.userPrefs.getUsername()
        password = container.userPrefs.getPassword()
        // El resumen ya trae lo esencial (tipo de error + qué cargaba); si no
        // existe, se usa la cola del log completo como respaldo.
        val resumen = CrashReporter.readResumen(context)
        crashReport = when {
            resumen.isNotBlank() -> resumen
            CrashReporter.read(context).contains("===== CRASH") ->
                CrashReporter.read(context).takeLast(6000)
            else -> null
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    fun connect() {
        val user = username.trim()
        if (user.isBlank() || password.isBlank()) {
            errorMessage = "Ingresá tu usuario y contraseña."
            return
        }
        loading = true
        scope.launch {
            when (val result = container.xtreamRepository.loginAuto(user, password)) {
                is LoginResult.Ok -> {
                    // Fila única estable: se actualiza con la URL efectiva y las credenciales.
                    val row = container.serverRepository.getOrCreateSingle()
                    val updated = row.copy(
                        name = "MTV",
                        url = result.session.baseUrl,
                        username = user,
                        password = password,
                    )
                    container.serverRepository.upsert(updated)
                    container.xtreamRepository.updateSessionServer(updated)
                    container.userPrefs.setLastServerId(row.id)
                    container.userPrefs.setCredentials(user, password)
                    loading = false
                    onConnected()
                }
                LoginResult.AuthFailed -> {
                    loading = false
                    errorMessage = "Usuario o contraseña inválidos."
                }
                is LoginResult.NetworkError -> {
                    loading = false
                    errorMessage = "No se pudo conectar con el servidor. Revisá tu conexión."
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MTV") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = ::connect,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Conectar") }
            }
        }
    }

    // Diagnóstico: si la app se cerró por un error, mostrarlo para copiarlo.
    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text("Se detectó un cierre") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("La última vez la app se cerró por este error. Copialo y pasalo por el chat:")
                    Spacer(Modifier.height(8.dp))
                    Text(report)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    errorMessage = "Reporte copiado."
                }) { Text("Copiar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        CrashReporter.clear(context)
                        crashReport = null
                    }
                }) { Text("Borrar") }
            },
        )
    }
}
