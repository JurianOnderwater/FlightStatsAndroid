package com.example.flightstats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0056C6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF006874),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF97F0FF),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF4A58A9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE0FF),
    onTertiaryContainer = Color(0xFF00105B),
    background = Color(0xFFF6F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceContainer = Color(0xFFF0F4FA),
    surfaceContainerHigh = Color(0xFFE6EDF8),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004394),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF4DD8EC),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = Color(0xFFBAC3FF),
    onTertiary = Color(0xFF172878),
    tertiaryContainer = Color(0xFF313F8F),
    onTertiaryContainer = Color(0xFFDEE0FF),
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF171C26),
    onSurface = Color(0xFFE1E2E8),
    surfaceContainer = Color(0xFF1E2430),
    surfaceContainerHigh = Color(0xFF262D3B),
    surfaceVariant = Color(0xFF2B313D),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF43474E)
)

@Composable
fun FlightStatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
