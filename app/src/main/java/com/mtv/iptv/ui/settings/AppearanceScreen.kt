package com.mtv.iptv.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.mtv.iptv.di.LocalAppContainer
import kotlinx.coroutines.launch

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

/** Apariencia: tema, idioma y orden del catálogo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.userPrefs
    val scope = rememberCoroutineScope()

    val theme by prefs.theme.collectAsState(initial = "sistema")
    val language by prefs.language.collectAsState(initial = "es")
    val defaultSort by prefs.defaultSort.collectAsState(initial = "nombre")

    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }

    fun setPref(action: suspend () -> Unit) = scope.launch { action() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apariencia") },
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
                Spacer(Modifier.height(24.dp))
            }
        }
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
}
