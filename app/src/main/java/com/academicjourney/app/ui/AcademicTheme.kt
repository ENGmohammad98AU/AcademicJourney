package com.academicjourney.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AcademicLightColors = lightColorScheme(
    primary = Color(0xFF105A62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F2EF),
    onPrimaryContainer = Color(0xFF143E45),
    secondary = Color(0xFFA98135),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4ECDA),
    onSecondaryContainer = Color(0xFF2C2200),
    background = Color(0xFFF7F6F1),
    onBackground = Color(0xFF203E42),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF203E42),
    surfaceVariant = Color(0xFFE9F2EF),
    onSurfaceVariant = Color(0xFF617579),
    surfaceContainer = Color(0xFFFFFEFA),
    surfaceContainerLow = Color(0xFFFFFEFA),
    surfaceContainerHigh = Color(0xFFF4ECDA),
    error = Color(0xFFA44543),
    errorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF617579),
    outlineVariant = Color(0xFFDFE7E3)
)

@Composable
fun AcademicJourneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AcademicLightColors,
        typography = Typography(),
        content = content
    )
}
