package com.steerin.parkingmaster.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pleos-style dark cockpit palette (12.3" display, night-safe contrast). */
object SteerinColors {
    val Background = Color(0xFF121418)
    val Surface = Color(0xFF1B1F26)
    val SurfaceHigh = Color(0xFF262B34)
    val Cyan = Color(0xFF00AAD2)      // ghost ring / brand
    val Green = Color(0xFF00E676)     // snapped / success
    val Coral = Color(0xFFFF5252)     // misaligned / danger
    val Amber = Color(0xFFFFC107)     // caution
    val Gold = Color(0xFFFFD86B)      // reward decoration only (never a coaching signal)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFF9AA3AF)
    val Outline = Color(0xFF343A45)
}

private val DarkScheme = darkColorScheme(
    primary = SteerinColors.Cyan,
    onPrimary = Color.Black,
    secondary = SteerinColors.Green,
    onSecondary = Color.Black,
    error = SteerinColors.Coral,
    background = SteerinColors.Background,
    onBackground = SteerinColors.Text,
    surface = SteerinColors.Surface,
    onSurface = SteerinColors.Text,
    surfaceVariant = SteerinColors.SurfaceHigh,
    onSurfaceVariant = SteerinColors.Muted,
    outline = SteerinColors.Outline,
)

/** Type scale tuned for arm's-length reading on an in-car display. */
private val SteerinTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 100.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
)

@Composable
fun SteerinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = SteerinTypography, content = content)
}
