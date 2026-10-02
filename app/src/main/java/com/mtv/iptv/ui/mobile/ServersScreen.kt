package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.mtv.iptv.data.local.db.ServerEntity
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.launch

/**
 * Bandera en memoria para el flujo de logout: "Cerrar sesión" la activa para
 * que ServersScreen muestre el formulario en vez de hacer auto-login con las
 * credenciales guardadas. Se consume una sola vez.
 */
object LoginFlowState {
    @Volatile
    var skipAutoLoginOnce: Boolean = false
}

/**
 * Login simplificado: solo usuario y contraseña. Los servidores (DNS) están
 * ocultos en el código y se prueban en orden hasta que uno acepta las
 * credenciales.
 *
 * Las credenciales se guardan CIFRADAS por servidor (SecurePrefs) y al abrir
 * la pantalla se hace login AUTOMÁTICO silencioso si hay credenciales
 * guardadas. "Cerrar sesión" (SettingsScreen) activa
 * [LoginFlowState.skipAutoLoginOnce] para mostrar el formulario sin auto-login.
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
    var savedServer by remember { mutableStateOf<ServerEntity?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    /** Login automático silencioso en curso (muestra "Conectando..."). */
    var autoLogin by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val secure = container.securePrefs
        val skip = LoginFlowState.skipAutoLoginOnce.also { LoginFlowState.skipAutoLoginOnce = false }

        // Credenciales para el auto-login: primero las cifradas del último
        // servidor; si no hay, se migran las viejas en plano (UserPrefs).
        var autoCreds: Pair<String, String>? = null
        if (!skip) {
            val lastId = secure.getLastServerId()
            autoCreds = if (lastId > 0) secure.getCredentials(lastId) else null
            if (autoCreds == null) {
                val oldUser = container.userPrefs.getUsername()
                val oldPass = container.userPrefs.getPassword()
                if (oldUser.isNotBlank() && oldPass.isNotBlank()) {
                    autoCreds = oldUser to oldPass
                } else {
                    username = oldUser
                    password = oldPass
                }
            }
        } else {
            // Logout: precargar el formulario con lo recordado, sin auto-login.
            val lastId = secure.getLastServerId()
            val remembered = if (lastId > 0) secure.getCredentials(lastId) else null
            username = remembered?.first ?: container.userPrefs.getUsername()
            password = remembered?.second ?: container.userPrefs.getPassword()
        }
        savedServer = container.serverRepository.getAll().firstOrNull()
        // El resumen ya trae lo esencial (tipo de error + qué cargaba); si no
        // existe, se usa la cola del log completo como respaldo.
        val resumen = CrashReporter.readResumen(context)
        crashReport = when {
            resumen.isNotBlank() -> resumen
            CrashReporter.read(context).contains("===== CRASH") ->
                CrashReporter.read(context).takeLast(6000)
            else -> null
        }

        // Login automático silencioso con las credenciales guardadas.
        val (autoUser, autoPass) = autoCreds ?: return@LaunchedEffect
        autoLogin = true
        when (val result = container.xtreamRepository.loginAuto(autoUser, autoPass)) {
            is LoginResult.Ok -> {
                val row = container.serverRepository.getOrCreateSingle()
                val updated = row.copy(
                    name = "MTV",
                    url = result.session.baseUrl,
                    username = autoUser,
                    password = autoPass,
                )
                container.serverRepository.upsert(updated)
                container.xtreamRepository.updateSessionServer(updated)
                // Guardar cifrado (migra las viejas en plano) + recordar servidor.
                secure.saveCredentials(row.id, autoUser, autoPass)
                secure.setLastServerId(row.id)
                container.userPrefs.setLastServerId(row.id)
                container.userPrefs.clearCredentials()
                savedServer = updated
                autoLogin = false
                onConnected()
            }
            else -> {
                // Falla: mostrar el formulario con los datos para reintentar.
                username = autoUser
                password = autoPass
                autoLogin = false
                errorMessage = "No se pudo conectar automáticamente. Revisá tus credenciales."
            }
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
                    // Guardar CIFRADO por servidor (SecurePrefs), no en plano.
                    container.securePrefs.saveCredentials(row.id, user, password)
                    container.securePrefs.setLastServerId(row.id)
                    container.userPrefs.setLastServerId(row.id)
                    container.userPrefs.clearCredentials()
                    savedServer = updated
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
        if (autoLogin) {
            // Login automático silencioso en curso.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Conectando...")
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
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

            // Servidor guardado: editar y borrar.
            savedServer?.let { srv ->
                Spacer(Modifier.height(32.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Servidor guardado", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(srv.name.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.bodyLarge)
                        if (srv.url.isNotBlank()) {
                            Text(
                                srv.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Usuario: ${srv.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showEdit = true }) { Text("Editar") }
                            Button(
                                onClick = { showDelete = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                            ) { Text("Borrar") }
                        }
                    }
                }
            }
        }
        }
    }

    // Editar servidor guardado: nombre, URL, usuario y clave.
    if (showEdit) {
        val srv = savedServer
        var editName by remember { mutableStateOf(srv?.name.orEmpty()) }
        var editUrl by remember { mutableStateOf(srv?.url.orEmpty()) }
        var editUser by remember { mutableStateOf(srv?.username.orEmpty()) }
        var editPass by remember { mutableStateOf(srv?.password.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Editar servidor") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editUser,
                        onValueChange = { editUser = it },
                        label = { Text("Usuario") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editPass,
                        onValueChange = { editPass = it },
                        label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (srv == null) {
                        showEdit = false
                        return@TextButton
                    }
                    if (editUser.isBlank() || editPass.isBlank()) {
                        errorMessage = "El usuario y la contraseña no pueden quedar vacíos."
                        return@TextButton
                    }
                    scope.launch {
                        val updated = srv.copy(
                            name = editName.trim(),
                            url = editUrl.trim(),
                            username = editUser.trim(),
                            password = editPass,
                        )
                        container.serverRepository.upsert(updated)
                        container.xtreamRepository.updateSessionServer(updated)
                        container.securePrefs.saveCredentials(srv.id, updated.username, updated.password)
                        container.securePrefs.setLastServerId(srv.id)
                        container.userPrefs.setLastServerId(srv.id)
                        container.userPrefs.clearCredentials()
                        username = updated.username
                        password = updated.password
                        savedServer = updated
                        showEdit = false
                        errorMessage = "Servidor actualizado."
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("Cancelar") }
            },
        )
    }

    // Borrar servidor guardado (con confirmación).
    if (showDelete) {
        val srv = savedServer
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Borrar servidor") },
            text = { Text("Se elimina el servidor guardado. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (srv != null) {
                            container.serverRepository.delete(srv)
                            container.securePrefs.clearCredentials(srv.id)
                            container.securePrefs.setLastServerId(-1)
                        }
                        container.userPrefs.clearCredentials()
                        savedServer = null
                        showDelete = false
                        errorMessage = "Servidor borrado."
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            },
        )
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
