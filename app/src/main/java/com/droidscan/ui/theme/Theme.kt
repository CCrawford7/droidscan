package com.droidscan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Signal Clarity palette (ux-droidscan-2026-08-04) ──
// Replaces the GitHub-dark palette. Deep telecom navy canvas, ONE cyan accent,
// sans labels + mono values. Status colors (ok/warn/crit) hold AA contrast
// against both bg and surface (verified: crit 6.77:1 is the floor).

/** Screen canvas. */
val TerminalBackground = Color(0xFF0B1220)       // bg
/** Tier-card fill. */
val TerminalSurface = Color(0xFF101A2E)          // surface
/** Elevated surfaces, tab-bar scrim. */
val TerminalSurfaceVariant = Color(0xFF16233C)   // raised
/** Card/track hairlines. */
val TerminalBorder = Color(0xFF1E2D4A)           // border
/** Primary text (values). */
val TerminalTextPrimary = Color(0xFFEAF1FA)      // ink
/** Labels, secondary text. */
val TerminalTextSecondary = Color(0xFF8CA0BE)    // ink2
/** The ONE cyan — tier labels, brand, expand affordance, active tab. */
val TerminalAccentBlue = Color(0xFF22D3EE)       // accent
/** Threshold tier: green. */
val TerminalAccentGreen = Color(0xFF34D399)      // ok
/** Threshold tier: yellow. */
val TerminalAccentYellow = Color(0xFFFBBF24)     // warn
/** Threshold tier: red + crisis. */
val TerminalAccentRed = Color(0xFFF87171)        // crit
/** "streaming/live" affordance, Power Mode badge. */
val TerminalAccentCyan = Color(0xFF60A5FA)       // live
/** Peak-temp color ramping (thermal only). */
val TerminalAccentOrange = Color(0xFFFF8800)     // peak

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
