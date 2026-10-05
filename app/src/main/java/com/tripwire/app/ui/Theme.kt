package com.tripwire.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Calm and plain: Tripwire speaks like a respectful relative (PRD 13.5). */
object TwColors {
    val Teal = Color(0xFF0B6E69)
    val TealLight = Color(0xFF7FD4CC)
}

/** One status tone: a strong colour for icons and words, and a container with its own text colour. */
@Immutable
data class Tone(val main: Color, val container: Color, val onContainer: Color)

/** Safe, caution and danger, tuned for each theme so text on them always has enough contrast. */
@Immutable
data class StatusColors(val safe: Tone, val caution: Tone, val danger: Tone)

private val LightStatus = StatusColors(
    safe = Tone(Color(0xFF1E6B34), Color(0xFFDDF2E1), Color(0xFF0A3818)),
    caution = Tone(Color(0xFF8A5A00), Color(0xFFFFE9C2), Color(0xFF3D2800)),
    danger = Tone(Color(0xFFB3261E), Color(0xFFFFDAD5), Color(0xFF410002)),
)

private val DarkStatus = StatusColors(
    safe = Tone(Color(0xFF8BD89B), Color(0xFF173A22), Color(0xFFC8F2D0)),
    caution = Tone(Color(0xFFF5C26B), Color(0xFF3F2E0A), Color(0xFFFFE3B0)),
    danger = Tone(Color(0xFFFFB4AB), Color(0xFF5C1814), Color(0xFFFFDAD5)),
)

private val LocalStatus = staticCompositionLocalOf { LightStatus }

/** Status colours for the current theme: `Status.colors.danger.main` and so on. */
object Status {
    val colors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatus.current
}

private val Light = lightColorScheme(
    primary = TwColors.Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9ECE5),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF051F1C),
    tertiary = Color(0xFF4A607C),
    onTertiary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF1A1C1C),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF1A1C1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4F3),
    surfaceContainer = Color(0xFFEAEFEE),
    surfaceContainerHigh = Color(0xFFE4EAE8),
    surfaceContainerHighest = Color(0xFFDEE4E2),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2E3131),
    inverseOnSurface = Color(0xFFEFF1F0),
)

private val Dark = darkColorScheme(
    primary = TwColors.TealLight,
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFA0F1E8),
    secondary = Color(0xFFB0CCC8),
    onSecondary = Color(0xFF1B3532),
    secondaryContainer = Color(0xFF324B48),
    onSecondaryContainer = Color(0xFFCCE8E3),
    tertiary = Color(0xFFB2C8E8),
    onTertiary = Color(0xFF1B314B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFDEE4E2),
    inverseOnSurface = Color(0xFF2B3130),
)

/** All warning text is at least 18 sp and scales with the system font size (PRD 13.4). */
private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val TwShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TripwireTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = TwShapes, content = content)
    }
}
