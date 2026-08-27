package com.example.dlmsconfigurator.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = AccentTeal,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BgElevated,
    onPrimaryContainer = TextPrimary,
    secondary = AccentBlue,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = BgBase,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = StatusError,
    onError = androidx.compose.ui.graphics.Color.White,
)

private val AppDarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF1F1),
    secondary = Color(0xFF8DBBFF),
    background = Color(0xFF101718),
    onBackground = Color(0xFFE0E5E5),
    surface = Color(0xFF172021),
    onSurface = Color(0xFFE0E5E5),
    surfaceVariant = Color(0xFF293536),
    onSurfaceVariant = Color(0xFFB9C9C9),
    outline = Color(0xFF718282),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun DLMSConfiguratorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColorScheme else AppColorScheme,
        typography  = Typography,
        content     = content
    )
}
