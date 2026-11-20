package com.smartumbrella.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = SurfaceWhite,
    primaryContainer = PrimaryTeal.copy(alpha = 0.1f),
    onPrimaryContainer = PrimaryTealDark,
    secondary = TealAccent,
    onSecondary = SurfaceWhite,
    background = SoftBackground,
    onBackground = TextNavyPrimary,
    surface = SurfaceWhite,
    onSurface = TextNavyPrimary,
    surfaceVariant = SoftBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = RainRed,
    onError = SurfaceWhite
)

@Composable
fun SmartUmbrellaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For this IoT showcase dashboard, we enforce the clean, soft off-white aesthetic
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

