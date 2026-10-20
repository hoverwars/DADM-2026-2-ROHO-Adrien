package com.example.reto_8_roho_a.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = IndigoDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0EA),
    onSecondaryContainer = Color(0xFF00382F),
    background = LightBackground,
    surface = Color.White,
    surfaceVariant = LightSurfaceVariant,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F1F8),
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = IndigoDark,
    primaryContainer = Color(0xFF3A4590),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = TealLight,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFC8F0EA),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
)

/**
 * Tema de la app. No se usa el "dynamic color" de Android 12+ para que la app
 * conserve siempre su identidad visual (índigo / verde azulado).
 */
@Composable
fun RETO_8_ROHO_ATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
