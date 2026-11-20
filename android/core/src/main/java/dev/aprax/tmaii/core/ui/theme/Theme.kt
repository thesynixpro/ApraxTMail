package dev.aprax.tmaii.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Blue + violet accent palette
val VioletPrimary = Color(0xFF8B7CF6)
val VioletOnPrimary = Color(0xFFFFFFFF)
val BluePrimary = Color(0xFF4F8CFF)
val BlueSecondary = Color(0xFF6EB6FF)
val SurfaceDark = Color(0xFF0E1017)
val SurfaceContainerDark = Color(0xFF171A23)
val SurfaceLight = Color(0xFFF6F7FB)
val OnSurfaceDark = Color(0xFFE8EAF2)
val OnSurfaceLight = Color(0xFF14161C)

private val DarkColors = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = VioletOnPrimary,
    secondary = BlueSecondary,
    tertiary = BluePrimary,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = Color(0xFFB6BAC8),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF1D212C),
    surfaceContainerHighest = Color(0xFF242836),
    outline = Color(0xFF3A3F50),
    error = Color(0xFFFF6B8A),
    onError = Color(0xFF2B0008),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4FE0),
    onPrimary = Color.White,
    secondary = Color(0xFF2E6FD8),
    tertiary = Color(0xFF4F8CFF),
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = Color.White,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFECEEF5),
    onSurfaceVariant = Color(0xFF4A4E5C),
    outline = Color(0xFFC3C6D2),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun ApraxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ApraxTypography,
        content = content,
    )
}
