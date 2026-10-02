package com.mtv.iptv.ui.mobile

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import coil.imageLoader
import com.mtv.iptv.BuildConfig
import com.mtv.iptv.data.remote.SpeedTest
import com.mtv.iptv.data.remote.xtream.LoginResult
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Contenido de configuración reutilizable (móvil y TV).
 * El coordinador cablea [onServers] a la navegación hacia ServersScreen y
 * [onLogout] a la navegación hacia la pantalla de servidores (cerrar sesión,
 * sin borrar credenciales).
 */
@Composable
fun SettingsContent(
    onServers: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // El downloadModule lo agrega otro worker en paralelo; expone downloadManager (Media3).
    val downloadManager: DownloadManager = container.downloadModule.downloadManager

    val defaultSpeed by prefs.defaultSpeed.collectAsState(initial = 1f)
    val autoplayNext by prefs.autoplayNext.collectAsState(initial = false)
    val pipEnabled by prefs.pipEnabled.collectAsState(initial = true)
    val resumeEnabled by prefs.resumeEnabled.collectAsState(initial = true)
    val dlQuality by prefs.dlQuality.collectAsState(initial = "alta")
    val dlWifiOnly by prefs.dlWifiOnly.collectAsState(initial = true)
    val theme by prefs.theme.collectAsState(initial = "sistema")
    val language by prefs.language.collectAsState(initial = "es")
    val defaultSort by prefs.defaultSort.collectAsState(initial = "nombre")
    val privateDns by prefs.privateDns.collectAsState(initial = false)
    val lastSpeedMbps by prefs.lastSpeedMbps.collectAsState(initial = -1f)
    val lastSpeedAt by prefs.lastSpeedAt.collectAsState(initial = 0L)
    val lastSpeedDns by prefs.lastSpeedDns.collectAsState(initial = "sistema")

    var speedDialog by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    var confirmForgetCreds by remember { mutableStateOf(false) }
    var usedBytes by remember { mutableStateOf(-1L) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Test de velocidad.
    var speedTestRunning by remember { mutableStateOf(false) }
    var speedResult by remember { mutableStateOf<SpeedTest.Result?>(null) }
    var showSpeedResult by remember { mutableStateOf(false) }

    // Multi-usuario.
    data class UserEntry(val id: Long, val label: String, val username: String)
    var users by remember { mutableStateOf<List<UserEntry>>(emptyList()) }
    var activeUserId by remember { mutableStateOf(-1L) }
    var confirmDeleteUser by remember { mutableStateOf<UserEntry?>(null) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    fun refreshUsedSpace() {
        usedBytes = calcUsedSpace(downloadManager)
    }

    LaunchedEffect(Unit) {
        refreshUsedSpace()
        refreshUsers()
    }

    fun showMessage(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    // ---------------- Multi-usuario ----------------

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

    // ---------------- Test de velocidad ----------------

    fun lastSpeedSubtitle(): String {
        if (lastSpeedMbps < 0) return "Medí tu conexión contra el servidor"
        return "Última: %.1f Mbps · %s · %s".format(
            lastSpeedMbps, SpeedTest.verdictFor(lastSpeedMbps.toDouble()), timeAgo(lastSpeedAt)
        )
    }

    fun runSpeedTest() = scope.launch {
        if (container.xtreamRepository.session == null) {
            showMessage("Iniciá sesión primero")
            return@launch
        }
        speedTestRunning = true
        try {
            val result = container.speedTest.run()
            speedResult = result
            showSpeedResult = true
            setPref { prefs.setLastSpeed(result.mbps.toFloat(), result.dnsLabel) }
        } catch (e: Exception) {
            showMessage("No se pudo medir: ${e.message ?: "error de red"}")
        } finally {
            speedTestRunning = false
        }
    }

    fun clearCache() = scope.launch {
        withContext(Dispatchers.IO) {
            try {
                context.imageLoader.diskCache?.clear()
            } catch (_: Exception) {
            }
            clearTempCache(context)
        }
        showMessage("Caché limpiado")
    }

    fun deleteAllDownloads() = scope.launch {
        val n = removeAllDownloads(downloadManager)
        refreshUsedSpace()
        showMessage(if (n == 0) "No hay descargas" else "Descargas eliminadas")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("REPRODUCCIÓN")
            SettingsRow(
                title = "Velocidad por defecto",
                subtitle = speedLabel(defaultSpeed),
                onClick = { speedDialog = true },
            )
            SettingsSwitch(
                title = "Siguiente episodio automático",
                subtitle = "Reproduce el próximo episodio al terminar uno",
                checked = autoplayNext,
                onCheckedChange = { setPref { prefs.setAutoplayNext(it) } },
            )
            SettingsSwitch(
                title = "Picture-in-Picture",
                subtitle = "Muestra el botón de ventana flotante en el reproductor",
                checked = pipEnabled,
                onCheckedChange = { setPref { prefs.setPipEnabled(it) } },
            )
            SettingsSwitch(
                title = "Continuar donde quedó",
                subtitle = "Pregunta si retomar la reproducción al abrir un video",
                checked = resumeEnabled,
                onCheckedChange = { setPref { prefs.setResumeEnabled(it) } },
            )

            SectionHeader("DESCARGAS")
            SettingsRow(
                title = "Calidad de descarga",
                subtitle = qualityOptions.firstOrNull { it.first == dlQuality }?.second ?: dlQuality,
                onClick = { qualityDialog = true },
            )
            SettingsSwitch(
                title = "Solo con Wi-Fi",
                subtitle = "Descargar solo con conexiones sin medidor",
                checked = dlWifiOnly,
                onCheckedChange = { enabled ->
                    setPref {
                        prefs.setDlWifiOnly(enabled)
                        downloadManager.setRequirements(
                            Requirements(
                                if (enabled) Requirements.NETWORK_UNMETERED
                                else Requirements.NETWORK
                            )
                        )
                    }
                },
            )
            SettingsRow(
                title = "Espacio usado",
                subtitle = formatBytes(usedBytes),
                onClick = null,
            )
            Button(
                onClick = { confirmDeleteDownloads = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Borrar todas las descargas") }

            SectionHeader("APARIENCIA")
            SettingsRow(
                title = "Tema",
                subtitle = themeOptions.firstOrNull { it.first == theme }?.second ?: theme,
                onClick = { themeDialog = true },
            )
            SettingsRow(
                title = "Idioma",
                subtitle = languageOptions.firstOrNull { it.first == language }?.second ?: language,
                onClick = { languageDialog = true },
            )
            SettingsRow(
                title = "Orden del catálogo",
                subtitle = sortOptions.firstOrNull { it.first == defaultSort }?.second ?: defaultSort,
                onClick = { sortDialog = true },
            )

            SectionHeader("RED")
            SettingsSwitch(
                title = "DNS privado (Cloudflare 1.1.1.1)",
                subtitle = "Evita bloqueos de tu proveedor de internet",
                checked = privateDns,
                onCheckedChange = { enabled ->
                    setPref {
                        prefs.setPrivateDns(enabled)
                        // Aplica de inmediato al proveedor HTTP (además de persistir).
                        container.httpClientProvider.usePrivateDns = enabled
                    }
                },
            )
            SettingsRow(
                title = "DNS activo",
                subtitle = if (privateDns) "Cloudflare 1.1.1.1" else "DNS del sistema",
                onClick = null,
            )
            SettingsRow(
                title = if (speedTestRunning) "Midiendo velocidad…" else "Probar velocidad",
                subtitle = lastSpeedSubtitle(),
                onClick = { if (!speedTestRunning) runSpeedTest() },
            )

            SectionHeader("SERVIDORES")
            SettingsRow(
                title = "Servidores",
                subtitle = "Gestionar servidores Xtream",
                onClick = onServers,
            )

            SectionHeader("USUARIOS")
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

            SectionHeader("CUENTA")
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

            SectionHeader("ACERCA DE")
            SettingsRow(
                title = "Versión",
                subtitle = BuildConfig.VERSION_NAME,
                onClick = null,
            )
            Button(
                onClick = { confirmClearCache = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Limpiar caché") }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (speedDialog) {
        OptionsDialog(
            title = "Velocidad por defecto",
            options = speedOptions.map { "$it" to "${it}x" },
            selected = "$defaultSpeed",
            onSelect = {
                setPref { prefs.setDefaultSpeed(it.toFloatOrNull() ?: 1f) }
                speedDialog = false
            },
            onDismiss = { speedDialog = false },
        )
    }
    if (qualityDialog) {
        OptionsDialog(
            title = "Calidad de descarga",
            options = qualityOptions,
            selected = dlQuality,
            onSelect = {
                setPref { prefs.setDlQuality(it) }
                qualityDialog = false
            },
            onDismiss = { qualityDialog = false },
        )
    }
    if (themeDialog) {
        OptionsDialog(
            title = "Tema",
            options = themeOptions,
            selected = theme,
            onSelect = {
                setPref { prefs.setTheme(it) }
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }
    if (languageDialog) {
        OptionsDialog(
            title = "Idioma",
            options = languageOptions,
            selected = language,
            onSelect = {
                setPref { prefs.setLanguage(it) }
                languageDialog = false
            },
            onDismiss = { languageDialog = false },
        )
    }
    if (sortDialog) {
        OptionsDialog(
            title = "Orden del catálogo",
            options = sortOptions,
            selected = defaultSort,
            onSelect = {
                // Aplica a sort_vod y sort_series también.
                setPref { prefs.setDefaultSort(it) }
                sortDialog = false
            },
            onDismiss = { sortDialog = false },
        )
    }
    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Limpiar caché") },
            text = { Text("Se borran las imágenes en caché y los archivos temporales. Las descargas no se tocan.") },
            confirmButton = {
                TextButton(onClick = { confirmClearCache = false; clearCache() }) { Text("Limpiar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Cancelar") }
            },
        )
    }
    if (confirmDeleteDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDownloads = false },
            title = { Text("Borrar descargas") },
            text = { Text("Se eliminan todas las descargas guardadas. ¿Seguro?") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteDownloads = false; deleteAllDownloads() }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDownloads = false }) { Text("Cancelar") }
            },
        )
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

    // Resultado del test de velocidad.
    if (showSpeedResult && speedResult != null) {
        val r = speedResult!!
        AlertDialog(
            onDismissRequest = { showSpeedResult = false },
            title = { Text("Velocidad de descarga") },
            text = {
                Column {
                    Text(
                        "%.1f Mbps".format(r.mbps),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(r.verdict)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (r.dnsLabel == "sistema") "DNS: sistema"
                        else "DNS: ${r.dnsLabel} ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedResult = false }) { Text("Cerrar") }
            },
        )
    }

    // Confirmar eliminar usuario.
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

/** Pantalla de configuración (móvil). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onServers: () -> Unit,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onAddUser: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SettingsContent(onServers = onServers, onLogout = onLogout, onHome = onHome, onAddUser = onAddUser)
        }
    }
}

// ---------------- Opciones ----------------

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(speed: Float): String = "${speedOptions.firstOrNull { it == speed } ?: speed}x"

private val qualityOptions = listOf(
    "auto" to "Automática",
    "alta" to "Alta",
    "media" to "Media",
    "baja" to "Baja",
)

private val themeOptions = listOf(
    "sistema" to "Sistema",
    "oscuro" to "Oscuro",
    "claro" to "Claro",
)

/** Por ahora solo español; no se inventan traducciones. */
private val languageOptions = listOf("es" to "Español")

private val sortOptions = listOf(
    "nombre" to "Nombre",
    "recientes" to "Recientes",
    "rating" to "Rating",
    "anio" to "Año",
)

// ---------------- Filas y diálogos ----------------

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    var rowModifier = Modifier
        .fillMaxWidth()
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

@Composable
private fun SettingsSwitch(
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

@Composable
private fun OptionsDialog(
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

// ---------------- Descargas (Media3) ----------------

/** Espacio usado = suma de bytes descargados según el índice de descargas. */
private fun calcUsedSpace(dm: DownloadManager): Long {
    val cursor = dm.downloadIndex.getDownloads()
    var total = 0L
    try {
        while (cursor.moveToNext()) {
            total += cursor.download.getBytesDownloaded()
        }
    } finally {
        cursor.close()
    }
    return total
}

private fun removeAllDownloads(dm: DownloadManager): Int {
    val cursor = dm.downloadIndex.getDownloads()
    var n = 0
    try {
        while (cursor.moveToNext()) {
            dm.removeDownload(cursor.download.request.id)
            n++
        }
    } finally {
        cursor.close()
    }
    return n
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "Calculando…"
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.1f MB".format(bytes / (1024.0 * 1024))
}

/** "hace 5 min", "hace 2 h", "ayer", etc. para la última medición. */
private fun timeAgo(timestampMs: Long): String {
    if (timestampMs <= 0) return "nunca"
    val mins = (System.currentTimeMillis() - timestampMs) / 60_000
    return when {
        mins < 1 -> "ahora mismo"
        mins < 60 -> "hace $mins min"
        mins < 60 * 24 -> "hace ${mins / 60} h"
        else -> "hace ${mins / (60 * 24)} d"
    }
}

/**
 * Borra temporales del cacheDir, EXCLUYENDO el directorio de descargas.
 * Se excluye cualquier carpeta llamada "downloads" (nombre típico del caché de Media3).
 */
private fun clearTempCache(context: Context) {
    fun clean(file: File) {
        if (file.isDirectory) {
            if (file.name == "downloads") return
            file.listFiles()?.forEach { clean(it) }
            file.delete()
        } else {
            file.delete()
        }
    }
    context.cacheDir.listFiles()?.forEach { clean(it) }
}
