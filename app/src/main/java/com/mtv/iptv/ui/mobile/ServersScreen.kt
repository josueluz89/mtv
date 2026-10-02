package com.mtv.iptv.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.local.db.ServerEntity
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(onConnected: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val servers by container.serverRepository.observeAll().collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerEntity?>(null) }
    var connectingId by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ServerEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { container.serverRepository.ensurePresets() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    fun connect(server: ServerEntity) {
        if (server.username.isBlank() || server.password.isBlank()) {
            errorMessage = "Ingresá tu usuario y contraseña del servidor con el botón de editar."
            return
        }
        connectingId = server.id
        scope.launch {
            when (val result = container.xtreamRepository.login(server)) {
                is LoginResult.Ok -> {
                    container.userPrefs.setLastServerId(server.id)
                    connectingId = null
                    onConnected()
                }
                LoginResult.AuthFailed -> {
                    connectingId = null
                    errorMessage = "Usuario o contraseña inválidos."
                }
                is LoginResult.NetworkError -> {
                    connectingId = null
                    errorMessage = "No se pudo conectar con el servidor. Revisá la URL y tu conexión."
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MTV — Servidores") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar servidor")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(servers, key = { it.id }) { server ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (server.username.isNotBlank()) {
                                Text(
                                    "Usuario: ${server.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (connectingId == server.id) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = { connect(server) }) {
                                Icon(Icons.Default.Login, contentDescription = "Conectar")
                            }
                            IconButton(onClick = { editing = server; showDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { deleteTarget = server }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ServerEditDialog(
            server = editing,
            onDismiss = { showDialog = false },
            onSave = { name, url, username, password ->
                scope.launch {
                    val entity = editing?.copy(name = name, url = url, username = username, password = password)
                        ?: ServerEntity(name = name, url = url, username = username, password = password)
                    container.serverRepository.upsert(entity)
                    showDialog = false
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Borrar servidor") },
            text = { Text("¿Borrar \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.serverRepository.delete(target) }
                    deleteTarget = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }
}

/** Diálogo compartido (celular y TV) para agregar/editar un servidor Xtream. */
@Composable
fun ServerEditDialog(
    server: ServerEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, username: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf(server?.name.orEmpty()) }
    var url by remember { mutableStateOf(server?.url.orEmpty()) }
    var username by remember { mutableStateOf(server?.username.orEmpty()) }
    var password by remember { mutableStateOf(server?.password.orEmpty()) }
    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "Agregar servidor" else "Editar servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre") }, isError = nameError,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it; urlError = false },
                    label = { Text("URL (ej: http://servidor.tv:8080)") }, isError = urlError,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                nameError = name.isBlank()
                urlError = url.isBlank()
                if (!nameError && !urlError) onSave(name.trim(), url.trim(), username.trim(), password)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
