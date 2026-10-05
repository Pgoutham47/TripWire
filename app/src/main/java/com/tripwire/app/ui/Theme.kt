package com.tripwire.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Calm and plain: Tripwire speaks like a respectful relative (PRD 13.5). */
object TwColors {
    val Ink = Color(0xFF1B1F23)
    val Teal = Color(0xFF0B6E69)
    val TealDark = Color(0xFF7FD4CC)
    val Warn = Color(0xFFB3261E)
    val WarnSurface = Color(0xFFFFF4F2)
    val WarnSurfaceDark = Color(0xFF2A1513)
    val Caution = Color(0xFF8A5A00)
    val Ok = Color(0xFF1E6B34)
}

private val Light = lightColorScheme(
    primary = TwColors.Teal,
    onPrimary = Color.White,
    secondary = Color(0xFF4A6360),
    error = TwColors.Warn,
    background = Color(0xFFFAFBFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF2F1),
    onBackground = TwColors.Ink,
    onSurface = TwColors.Ink,
)

private val Dark = darkColorScheme(
    primary = TwColors.TealDark,
    onPrimary = Color(0xFF00201E),
    secondary = Color(0xFFB0CCC8),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111413),
    surface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFF2A3130),
    onBackground = Color(0xFFE1E3E2),
    onSurface = Color(0xFFE1E3E2),
)

/** All warning text is at least 18 sp and scales with the system font size (PRD 13.4). */
private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun TripwireTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
