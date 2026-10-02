package com.mtv.iptv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.mobile.LoginFlowState
import kotlinx.coroutines.launch

private data class UserEntry(val id: Long, val label: String, val username: String)

/**
 * Usuarios: lista de usuarios guardados (multi-usuario Xtream), cambiar de
 * usuario, agregar uno nuevo, cerrar sesión y olvidar credenciales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<UserEntry>>(emptyList()) }
    var activeUserId by remember { mutableStateOf(-1L) }
    var confirmDeleteUser by remember { mutableStateOf<UserEntry?>(null) }
    var confirmForgetCreds by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    /** Usuarios guardados = filas de servidor con credenciales cifradas. */
    fun refreshUsers() = scope.launch {
        val secure = container.securePrefs
        val servers = container.serverRepository.getAll()
        users = servers
            .filter { secure.hasCredentials(it.id) }
            .map { s ->
                val host = s.url.substringAfter("://").substringBefore("/").ifBlank { s.url }
                UserEntry(
                    id = s.id,
                    label = s.name.ifBlank { host }.ifBlank { "Usuario" },
                    username = s.username,
                )
            }
        activeUserId = secure.getLastServerId()
    }

    /** Cambia al usuario indicado: login + lo marca activo + vuelve al inicio. */
    fun switchUser(user: UserEntry) = scope.launch {
        val creds = container.securePrefs.getCredentials(user.id)
        if (creds == null) {
            showMessage("Sin credenciales guardadas")
            return@launch
        }
        showMessage("Conectando como ${user.username}…")
        when (val r = container.xtreamRepository.loginAuto(creds.first, creds.second)) {
            is LoginResult.Ok -> {
                val server = container.serverRepository.getById(user.id)
                if (server != null) {
                    val updated = server.copy(url = r.session.baseUrl, username = creds.first, password = "")
                    container.serverRepository.upsert(updated)
                    container.xtreamRepository.updateSessionServer(updated)
                }
                container.securePrefs.setLastServerId(user.id)
                container.userPrefs.setLastServerId(user.id)
                onHome()
            }
            LoginResult.AuthFailed -> showMessage("Usuario o contraseña inválidos")
            is LoginResult.NetworkError -> showMessage("No se pudo conectar con el servidor")
        }
    }

    fun deleteUser(user: UserEntry) = scope.launch {
        val server = container.serverRepository.getById(user.id)
        if (server != null) container.serverRepository.delete(server)
        container.securePrefs.clearCredentials(user.id)
        val wasActive = user.id == container.securePrefs.getLastServerId()
        if (wasActive) {
            container.xtreamRepository.logout()
            onLogout()
        } else {
            refreshUsers()
            showMessage("Usuario eliminado")
        }
    }

    LaunchedEffect(Unit) { refreshUsers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                users.forEach { user ->
                    SettingsRow(
                        title = (if (user.id == activeUserId) "● " else "") + user.label,
                        subtitle = user.username + if (user.id == activeUserId) " · Activo" else "",
                        onClick = { if (user.id != activeUserId) switchUser(user) },
                        trailing = {
                            IconButton(onClick = { confirmDeleteUser = user }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar usuario")
                            }
                        },
                    )
                }
                SettingsRow(
                    title = "Agregar usuario",
                    subtitle = "Guardar otro servidor + usuario + clave",
                    onClick = onAddUser,
                    trailing = {
                        Icon(Icons.Default.Add, contentDescription = null)
                    },
                )
                Spacer(Modifier.height(16.dp))
                SettingsRow(
                    title = "Cerrar sesión",
                    subtitle = "Vuelve a la pantalla de servidores (no borra las credenciales)",
                    onClick = {
                        // Evita que ServersScreen haga auto-login al llegar.
                        LoginFlowState.skipAutoLoginOnce = true
                        onLogout()
                    },
                )
                SettingsRow(
                    title = "Olvidar credenciales guardadas",
                    subtitle = "Borra el usuario y la contraseña guardados en este dispositivo",
                    onClick = { confirmForgetCreds = true },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmForgetCreds) {
        AlertDialog(
            onDismissRequest = { confirmForgetCreds = false },
            title = { Text("Olvidar credenciales") },
            text = { Text("Se borran el usuario y la contraseña guardados. La próxima vez tendrás que escribirlos de nuevo. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForgetCreds = false
                    scope.launch {
                        container.securePrefs.clearAll()
                        container.userPrefs.clearCredentials()
                        refreshUsers()
                        showMessage("Credenciales olvidadas")
                    }
                }) { Text("Olvidar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetCreds = false }) { Text("Cancelar") }
            },
        )
    }

    val userToDelete = confirmDeleteUser
    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteUser = null },
            title = { Text("Eliminar usuario") },
            text = { Text("Se borra ${userToDelete.username} (${userToDelete.label}) de este dispositivo. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteUser = null
                    deleteUser(userToDelete)
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteUser = null }) { Text("Cancelar") }
            },
        )
    }
}
