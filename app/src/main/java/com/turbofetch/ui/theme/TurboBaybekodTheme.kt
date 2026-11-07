package com.turbofetch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ТУРБОВАЙБКОД — шуточная тема для fun-ветки.
 *
 * Собрана из всего, что нейросети обычно тащат в интерфейсы: кислотный
 * неон, градиенты, тени, жирные обводки, эмодзи вместо иконок и текст
 * капсом с межбуквенным разрывом. Работать должно, но выглядит так, будто
 * приложение нарисовали за десять минут и не смотрели на результат.
 */

/** Кислотные неоновые цвета. */
private val Acid = Color(0xFF39FF14)      // зелёный люминофор
private val HotPink = Color(0xFFFF2D95)    // розовый неон
private val Cyan = Color(0xFF00E5FF)       // циановый
private val Purple = Color(0xFFB14BFF)     // фиолетовый
private val Orange = Color(0xFFFF6B35)     // оранжевый
private val Deep = Color(0xFF0A0014)       // почти чёрный с фиолетовым
private val DeepSurface = Color(0xFF1A0030)
private val Ink = Color(0xFFFFFFFF)        // белый текст

/**
 * Градиент для кнопок и заголовков — неоновая радуга.
 *
 * Порядок цветов важен: он задаёт «направление» перелива.
 */
val BaybekodGradient = listOf(HotPink, Purple, Cyan, Acid)

/** Второй градиент — холоднее, для рамок и линий. */
val BaybekodGradientAlt = listOf(Cyan, Acid, Orange, HotPink)

/**
 * Схема ТУРБОВАЙБКОДа. Тёмная по построению: на светлом фоне кислотный
 * неон выцветает и выглядит просто грязным.
 */
private val TurboBaybekodColors = darkColorScheme(
    primary = Acid,
    onPrimary = Deep,
    primaryContainer = HotPink,
    onPrimaryContainer = Ink,
    inversePrimary = HotPink,

    secondary = Cyan,
    onSecondary = Deep,
    secondaryContainer = Purple,
    onSecondaryContainer = Ink,

    tertiary = Orange,
    onTertiary = Deep,
    tertiaryContainer = HotPink,
    onTertiaryContainer = Ink,

    background = Deep,
    onBackground = Ink,
    surface = Deep,
    onSurface = Ink,
    surfaceVariant = DeepSurface,
    onSurfaceVariant = Cyan,
    surfaceContainer = DeepSurface,
    surfaceContainerHigh = Color(0xFF240044),
    surfaceContainerHighest = Color(0xFF2E0055),
    surfaceContainerLow = Color(0xFF120024),
    surfaceContainerLowest = Deep,
    surfaceBright = Color(0xFF3A0066),
    surfaceDim = Color(0xFF06000C),

    outline = Acid,
    outlineVariant = Purple,
    scrim = Color(0xCC000000),
    error = Color(0xFFFF1744),
    onError = Ink,
    errorContainer = Color(0xFF5A0016),
    onErrorContainer = Color(0xFFFFD9E0),
)

/**
 * Типографика ТУРБОВАЙБКОДа: капс с большим разрывом, жирный и с
 * обводкой-тенью через дублирование строк.
 */
private val TurboBaybekodTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = 3.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 2.5.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 2.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.5.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        letterSpacing = 1.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.6.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.2.sp,
    ),
)

/** Схема ТУРБОВАЙБКОДа. */
object TurboBaybekod {
    val colors = TurboBaybekodColors
    val typography = TurboBaybekodTypography
}
