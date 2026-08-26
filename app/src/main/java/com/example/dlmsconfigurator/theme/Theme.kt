package com.example.dlmsconfigurator.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun DLMSConfiguratorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}
