package com.turbofetch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.turbofetch.domain.model.AppTheme

/**
 * «Warm Paper» — минималистичная светлая тема: бумажный фон, чернильный текст,
 * волосяные линии вместо заливок. Динамический цвет отключён намеренно:
 * он ломает ощущение бумаги и подставляет чужую палитру.
 */

private val Paper = Color(0xFFFAF9F6)
private val Ink = Color(0xFF16181C)
private val InkSoft = Color(0xFF6B6862)
private val InkFaint = Color(0xFF8A8578)
private val Hairline = Color(0xFFDDD8CC)
private val Wash = Color(0xFFF1EEE6)
private val Rust = Color(0xFF9C4A2F)

private val PaperColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Ink,
    onPrimaryContainer = Paper,
    inversePrimary = Paper,
    secondary = InkSoft,
    onSecondary = Paper,
    secondaryContainer = Wash,
    onSecondaryContainer = Ink,
    tertiary = Rust,
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFF3E4DE),
    onTertiaryContainer = Rust,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Wash,
    onSurfaceVariant = InkFaint,
    surfaceContainer = Paper,
    surfaceContainerHigh = Color(0xFFF5F3ED),
    surfaceContainerHighest = Wash,
    surfaceContainerLow = Paper,
    surfaceContainerLowest = Paper,
    surfaceBright = Paper,
    surfaceDim = Color(0xFFF1EEE6),
    outline = Hairline,
    outlineVariant = Color(0xFFE8E4DA),
    scrim = Color(0x99000000),
    error = Color(0xFFA3352B),
    onError = Paper,
    errorContainer = Color(0xFFF6E3E0),
    onErrorContainer = Color(0xFF7A241C),
)

// Тёмная палитра оставлена на будущее: если понадобится тёмный режим.
@Suppress("unused")
private val PaperDarkColors = darkColorScheme(
    primary = Color(0xFFE8E4DA),
    onPrimary = Color(0xFF16181C),
    primaryContainer = Color(0xFFE8E4DA),
    onPrimaryContainer = Color(0xFF16181C),
    inversePrimary = Color(0xFF16181C),
    secondary = Color(0xFFB5AFA4),
    onSecondary = Color(0xFF16181C),
    secondaryContainer = Color(0xFF262521),
    onSecondaryContainer = Color(0xFFE8E4DA),
    tertiary = Color(0xFFD98A6A),
    onTertiary = Color(0xFF16181C),
    tertiaryContainer = Color(0xFF3A231B),
    onTertiaryContainer = Color(0xFFEFC4B2),
    background = Color(0xFF131311),
    onBackground = Color(0xFFE8E4DA),
    surface = Color(0xFF131311),
    onSurface = Color(0xFFE8E4DA),
    surfaceVariant = Color(0xFF1E1D1A),
    onSurfaceVariant = Color(0xFF9A948A),
    surfaceContainer = Color(0xFF1A1917),
    surfaceContainerHigh = Color(0xFF232220),
    surfaceContainerHighest = Color(0xFF2B2A27),
    surfaceContainerLow = Color(0xFF131311),
    surfaceContainerLowest = Color(0xFF0D0D0C),
    surfaceBright = Color(0xFF32302C),
    surfaceDim = Color(0xFF131311),
    outline = Color(0xFF3A3833),
    outlineVariant = Color(0xFF2A2926),
    scrim = Color(0xB3000000),
    error = Color(0xFFE39289),
    onError = Color(0xFF2A100D),
    errorContainer = Color(0xFF4A1F19),
    onErrorContainer = Color(0xFFF6D3CE),
)

/**
 * Заголовки — антиква (системный serif), текст — гротеск.
 * Так выглядит «бумажный» типографический контраст без единого файла шрифта.
 */
private val PaperTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp,
    ),
)

/**
 * Тема приложения.
 *
 * Тема и её вариант (светлый или тёмный) выбираются пользователем в
 * настройках. Динамический цвет отключён намеренно: он ломает ощущение
 * бумаги и подставляет чужую палитру.
 *
 * @param theme выбранная тема.
 * @param dark тёмный вариант.
 */
@Composable
fun TurboFetchTheme(
    theme: AppTheme = AppTheme.WARM_PAPER,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    // LocalAppTheme нужен экранам: они не знают, какая тема активна, и
    // спрашивают акценты у слоя Accent.
    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = if (dark) PaperDarkColors else PaperColors,
            typography = PaperTypography,
            content = content,
        )
    }
}
