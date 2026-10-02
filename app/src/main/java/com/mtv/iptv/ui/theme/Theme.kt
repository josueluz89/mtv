package com.mtv.iptv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MtvDarkColors = darkColorScheme(
    primary = Color(0xFFE02020),
    onPrimary = Color.White,
    secondary = Color(0xFFF0B429),
    onSecondary = Color.Black,
    tertiary = Color(0xFFF0B429),
    background = Color(0xFF0D0B0C),
    surface = Color(0xFF171315),
    surfaceVariant = Color(0xFF221C1E),
    onBackground = Color(0xFFF5F0EB),
    onSurface = Color(0xFFF5F0EB),
    onSurfaceVariant = Color(0xFFB8AFA8),
)

private val MtvLightColors = lightColorScheme(
    primary = Color(0xFFE02020),
    onPrimary = Color.White,
    secondary = Color(0xFFF0B429),
    onSecondary = Color.Black,
    tertiary = Color(0xFFF0B429),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1ECE7),
    onBackground = Color(0xFF1A1411),
    onSurface = Color(0xFF1A1411),
    onSurfaceVariant = Color(0xFF6E625B),
)

/**
 * Tema de la app.
 *
 * @param theme "sistema" | "oscuro" | "claro". "sistema" usa
 *   isSystemInDarkTheme(); con cualquier otro valor inesperado se usa el
 *   oscuro. El default "sistema" respeta la firma de los llamados existentes.
 */
@Composable
fun MtvTheme(theme: String = "sistema", content: @Composable () -> Unit) {
    val darkTheme = when (theme) {
        "oscuro" -> true
        "claro" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) MtvDarkColors else MtvLightColors,
        content = content,
    )
}
