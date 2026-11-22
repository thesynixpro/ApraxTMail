package dev.aprax.tmaii.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.aprax.tmaii.core.model.Accent
import dev.aprax.tmaii.core.model.ThemeMode

/** True while Liquid Glass mode is active — components layer translucent surfaces. */
val LocalGlassMode = staticCompositionLocalOf { false }

private data class AccentPalette(val lightPrimary: Color, val darkPrimary: Color, val darkContainer: Color)

private fun Accent.palette(): AccentPalette = when (this) {
    Accent.VIOLET -> AccentPalette(Color(0xFF5B4FE0), Color(0xFF8B7CF6), Color(0xFF2C2456))
    Accent.BLUE -> AccentPalette(Color(0xFF2E6FD8), Color(0xFF4F8CFF), Color(0xFF17264A))
    Accent.TEAL -> AccentPalette(Color(0xFF0F9E8E), Color(0xFF2DD4BF), Color(0xFF0C3B37))
    Accent.GREEN -> AccentPalette(Color(0xFF1D9E5A), Color(0xFF4ADE80), Color(0xFF123B24))
    Accent.AMBER -> AccentPalette(Color(0xFFB45309), Color(0xFFFBBF24), Color(0xFF43301A))
    Accent.ROSE -> AccentPalette(Color(0xFFE11D48), Color(0xFFFB7185), Color(0xFF451526))
}

private val DarkColors = darkColorScheme(
    background = Color(0xFF0E1017),
    onBackground = Color(0xFFE8EAF2),
    surface = Color(0xFF0E1017),
    onSurface = Color(0xFFE8EAF2),
    surfaceVariant = Color(0xFF171A23),
    onSurfaceVariant = Color(0xFFB6BAC8),
    surfaceContainer = Color(0xFF171A23),
    surfaceContainerHigh = Color(0xFF1D212C),
    surfaceContainerHighest = Color(0xFF242836),
    outline = Color(0xFF3A3F50),
    error = Color(0xFFFF6B8A),
    onError = Color(0xFF2B0008),
)

private val LightColors = lightColorScheme(
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14161C),
    surface = Color.White,
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFECEEF5),
    onSurfaceVariant = Color(0xFF4A4E5C),
    outline = Color(0xFFC3C6D2),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val GlassColors = darkColorScheme(
    background = Color(0xFF090B12),
    onBackground = Color(0xFFF2F4FA),
    surface = Color(0xFF14FFFFFF),
    onSurface = Color(0xFFF2F4FA),
    surfaceVariant = Color(0xFF1AFFFFFF),
    onSurfaceVariant = Color(0xFFC9D0E0),
    surfaceContainer = Color(0xFF14FFFFFF),
    surfaceContainerHigh = Color(0xFF1EFFFFFF),
    surfaceContainerHighest = Color(0xFF28FFFFFF),
    outline = Color(0x4DFFFFFF),
    outlineVariant = Color(0x33FFFFFF),
    error = Color(0xFFFF7A90),
    onError = Color(0xFF2B0008),
)

private fun withAccent(base: androidx.compose.material3.ColorScheme, accent: Accent): androidx.compose.material3.ColorScheme {
    val p = accent.palette()
    val primary = if (base == LightColors) p.lightPrimary else p.darkPrimary
    val onPrimary = if (base == LightColors) Color.White else Color(0xFF0B0D14)
    val primaryContainer = if (base == GlassColors) Color(0x33FFFFFF) else if (base == LightColors) p.lightPrimary.copy(alpha = 0.12f) else p.darkContainer
    val onPrimaryContainer = if (base == LightColors) p.lightPrimary else if (base == GlassColors) Color.White else Color(0xFFEAE6FF)
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary.copy(alpha = 0.85f),
        onSecondary = onPrimary,
        tertiary = primary,
        onTertiary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
    )
}

@Composable
fun ApraxTheme(
    mode: ThemeMode,
    accent: Accent,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val base = when (mode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.LIQUID_GLASS -> GlassColors
        ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
    }
    val colorScheme = withAccent(base, accent)
    CompositionLocalProvider(LocalGlassMode provides (mode == ThemeMode.LIQUID_GLASS)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ApraxTypography,
            content = content,
        )
    }
}
