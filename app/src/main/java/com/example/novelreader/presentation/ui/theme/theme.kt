package com.example.novelreader.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFFAFC7FF),
    onPrimary        = Color(0xFF092B5D),
    primaryContainer = Color(0xFF244779),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary        = Color(0xFF80D8C4),
    onSecondary      = Color(0xFF00382F),
    secondaryContainer = Color(0xFF11574B),
    onSecondaryContainer = Color(0xFFA7F4E2),
    tertiary         = Color(0xFFF0C36A),
    onTertiary       = Color(0xFF3E2D00),
    tertiaryContainer = Color(0xFF5C4309),
    onTertiaryContainer = Color(0xFFFFDEA0),
    background       = Color(0xFF111318),
    onBackground     = Color(0xFFE3E2E8),
    surface          = Color(0xFF171A20),
    onSurface        = Color(0xFFE3E2E8),
    surfaceVariant   = Color(0xFF434750),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline          = Color(0xFF8E919B)
)

private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF315F9F),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary        = Color(0xFF006B5A),
    onSecondary      = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9EF2DD),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary         = Color(0xFF7A5A00),
    onTertiary       = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA0),
    onTertiaryContainer = Color(0xFF261A00),
    background       = Color(0xFFFBFAF7),
    onBackground     = Color(0xFF1C1B20),
    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF1C1B20),
    surfaceVariant   = Color(0xFFE2E5EE),
    onSurfaceVariant = Color(0xFF444751),
    outline          = Color(0xFF747780)
)

@Composable
fun AINovelReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Edge-to-edge status bar styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

// ---- Typography ----
private val AppTypography = Typography(
    // Override key styles for novel reading context
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
    )
)
