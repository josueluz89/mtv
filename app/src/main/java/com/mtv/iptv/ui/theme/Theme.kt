package com.mtv.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun MtvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MtvDarkColors,
        content = content,
    )
}
