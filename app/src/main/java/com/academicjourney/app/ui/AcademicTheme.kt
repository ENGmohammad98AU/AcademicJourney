package com.academicjourney.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AcademicLightColors = lightColorScheme(
    primary = Color(0xFF075B78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0F8),
    onPrimaryContainer = Color(0xFF003544),
    secondary = Color(0xFF8A6A1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9AA),
    onSecondaryContainer = Color(0xFF2C2200),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C1F),
    surface = Color.White,
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFE8EEF2),
    onSurfaceVariant = Color(0xFF41484C),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF70787C)
)

@Composable
fun AcademicJourneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AcademicLightColors,
        typography = Typography(),
        content = content
    )
}
