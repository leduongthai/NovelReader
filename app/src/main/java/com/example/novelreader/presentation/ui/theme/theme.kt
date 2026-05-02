package com.example.novelreader.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF7BB3F0),
    onPrimary        = Color(0xFF003060),
    primaryContainer = Color(0xFF004886),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary        = Color(0xFF7BC8B2),
    onSecondary      = Color(0xFF003729),
    background       = Color(0xFF1A1C1E),
    onBackground     = Color(0xFFE2E2E6),
    surface          = Color(0xFF1A1C1E),
    onSurface        = Color(0xFFE2E2E6),
    surfaceVariant   = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF)
)

private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF1860AC),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary        = Color(0xFF196950),
    onSecondary      = Color(0xFFFFFFFF),
    background       = Color(0xFFFAFCFF),
    onBackground     = Color(0xFF1A1C1E),
    surface          = Color(0xFFFAFCFF),
    onSurface        = Color(0xFF1A1C1E),
    surfaceVariant   = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF42474E)
)

@Composable
fun AINovelReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,       // Material You on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

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
