package com.droidscan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TerminalBackground = Color(0xFF0D1117)
val TerminalSurface = Color(0xFF161B22)
val TerminalSurfaceVariant = Color(0xFF21262D)
val TerminalBorder = Color(0xFF30363D)
val TerminalTextPrimary = Color(0xFFC9D1D9)
val TerminalTextSecondary = Color(0xFF8B949E)
val TerminalAccentGreen = Color(0xFF4CAF50)
val TerminalAccentYellow = Color(0xFFFFC107)
val TerminalAccentRed = Color(0xFFF44336)
val TerminalAccentBlue = Color(0xFF58A6FF)
val TerminalAccentCyan = Color(0xFF39D2C0)
val TerminalAccentOrange = Color(0xFFFF8800)

private val DroidscanDarkColorScheme = darkColorScheme(
    primary = TerminalAccentBlue,
    onPrimary = TerminalBackground,
    primaryContainer = TerminalAccentBlue.copy(alpha = 0.15f),
    secondary = TerminalAccentGreen,
    onSecondary = TerminalBackground,
    tertiary = TerminalAccentCyan,
    background = TerminalBackground,
    onBackground = TerminalTextPrimary,
    surface = TerminalSurface,
    onSurface = TerminalTextPrimary,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TerminalTextSecondary,
    outline = TerminalBorder,
    error = TerminalAccentRed,
    onError = TerminalBackground
)

@Composable
fun DroidscanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DroidscanDarkColorScheme,
        content = content
    )
}
