package com.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Axio-inspired personal finance palette: teal primary, soft mint canvas,
 * high-contrast spend figures (light-first like axio.co.in PFM).
 */
object ExpenseColors {
    val Teal = Color(0xFF0F766E)
    val TealBright = Color(0xFF14B8A6)
    val TealDeep = Color(0xFF115E59)
    val Coral = Color(0xFFEA580C)
    val MintBg = Color(0xFFF0FDFA)
    val MintMuted = Color(0xFFCCFBF1)
    val Ink = Color(0xFF134E4A)
    val InkSoft = Color(0xFF5F7A76)
    val Card = Color(0xFFFFFFFF)
    val Border = Color(0xFF99F6E4)
    val Destructive = Color(0xFFDC2626)
    val Income = Color(0xFF059669)
    val Spend = Color(0xFF0F766E)

    val DarkBg = Color(0xFF042F2E)
    val DarkCard = Color(0xFF0F3D3A)
    val DarkMuted = Color(0xFF134E4A)
    val DarkFg = Color(0xFFF0FDFA)
    val DarkMutedFg = Color(0xFF99F6E4)
}

private val LightScheme = lightColorScheme(
    primary = ExpenseColors.Teal,
    onPrimary = Color.White,
    primaryContainer = ExpenseColors.MintMuted,
    onPrimaryContainer = ExpenseColors.TealDeep,
    secondary = ExpenseColors.Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = ExpenseColors.TealBright,
    onTertiary = Color.White,
    background = ExpenseColors.MintBg,
    onBackground = ExpenseColors.Ink,
    surface = ExpenseColors.Card,
    onSurface = ExpenseColors.Ink,
    surfaceVariant = Color(0xFFE6FFFA),
    onSurfaceVariant = ExpenseColors.InkSoft,
    outline = ExpenseColors.Border,
    outlineVariant = Color(0xFFB6EBE3),
    error = ExpenseColors.Destructive,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = ExpenseColors.TealBright,
    onPrimary = Color(0xFF003732),
    primaryContainer = ExpenseColors.TealDeep,
    onPrimaryContainer = ExpenseColors.MintMuted,
    secondary = Color(0xFFFB923C),
    onSecondary = Color(0xFF431407),
    secondaryContainer = Color(0xFF9A3412),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = ExpenseColors.Teal,
    onTertiary = Color.White,
    background = ExpenseColors.DarkBg,
    onBackground = ExpenseColors.DarkFg,
    surface = ExpenseColors.DarkCard,
    onSurface = ExpenseColors.DarkFg,
    surfaceVariant = ExpenseColors.DarkMuted,
    onSurfaceVariant = ExpenseColors.DarkMutedFg,
    outline = Color(0xFF2DD4BF),
    outlineVariant = Color(0xFF115E59),
    error = ExpenseColors.Destructive,
    onError = Color.White,
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun ExpenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
