package com.sonicwave.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SonicWaveDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A3E),
    onSurfaceVariant = Color(0xFFAAAAAA),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    outline = Color(0xFF444444)
)

@Composable
fun SonicWaveRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SonicWaveDarkColorScheme,
        content = content
    )
}
