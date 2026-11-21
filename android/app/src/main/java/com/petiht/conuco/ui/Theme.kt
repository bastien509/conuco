package com.petiht.conuco.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Field green, sun yellow, soil brown. Light: white. Dark: matte. No purple anywhere. */
data class Cc(
    val bg: Color, val surface: Color, val card: Color, val line: Color, val ink: Color, val muted: Color,
    val green: Color, val onGreen: Color, val greenSoft: Color, val sun: Color, val sunSoft: Color,
    val red: Color, val redSoft: Color, val amber: Color, val amberSoft: Color, val cloud: Color, val blue: Color, val dark: Boolean,
)
private val Light = Cc(
    bg = Color.White, surface = Color(0xFFF4F7F3), card = Color.White, line = Color(0xFFDFE7E0), ink = Color(0xFF10231A), muted = Color(0xFF5B6D63),
    green = Color(0xFF1F6F3F), onGreen = Color.White, greenSoft = Color(0xFFE6F2EA), sun = Color(0xFFF5C518), sunSoft = Color(0xFFFFF6D1),
    red = Color(0xFFC0392B), redSoft = Color(0xFFFDECEA), amber = Color(0xFFB45309), amberSoft = Color(0xFFFFF1DC), cloud = Color(0xFF0B3A66), blue = Color(0xFF1D4ED8), dark = false,
)
private val Dark = Cc(
    bg = Color(0xFF0F1412), surface = Color(0xFF141B17), card = Color(0xFF171F1B), line = Color(0xFF26302B), ink = Color(0xFFE8EFE9), muted = Color(0xFF9DB0A5),
    green = Color(0xFF4FB477), onGreen = Color(0xFF07140C), greenSoft = Color(0xFF18291F), sun = Color(0xFFF5C518), sunSoft = Color(0xFF2A2612),
    red = Color(0xFFEF6B5B), redSoft = Color(0xFF2C1714), amber = Color(0xFFF0A64A), amberSoft = Color(0xFF2B2112), cloud = Color(0xFF2F6CA8), blue = Color(0xFF6C9CFF), dark = true,
)

val cc: Cc
    @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Dark else Light

@Composable
fun ConucoTheme(content: @Composable () -> Unit) {
    val c = cc
    // Every Material slot set explicitly: Material 3 defaults are lavender, which this app never shows.
    val scheme = (if (c.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.green, onPrimary = c.onGreen, primaryContainer = c.greenSoft, onPrimaryContainer = c.green,
        secondary = c.sun, onSecondary = Color(0xFF1F1A00), secondaryContainer = c.sunSoft, onSecondaryContainer = c.ink,
        tertiary = c.amber, onTertiary = Color.White, tertiaryContainer = c.amberSoft, onTertiaryContainer = c.ink,
        background = c.surface, onBackground = c.ink, surface = c.card, onSurface = c.ink, surfaceVariant = c.surface, onSurfaceVariant = c.muted,
        surfaceTint = c.green, inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = c.greenSoft,
        surfaceBright = c.card, surfaceDim = c.surface, surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerHighest = c.surface,
        surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg, outline = c.line, outlineVariant = c.line, error = c.red, onError = Color.White,
        errorContainer = c.redSoft, onErrorContainer = c.red, scrim = Color(0x99000000),
    )
    val t = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        ),
        content = content,
    )
}
