package com.turbofetch.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbofetch.domain.model.AppTheme

/**
 * «Акценты» — слой поверх темы.
 *
 * Идея такая: экраны не знают, какая тема активна. Они просят у темы
 * *кисть* (`accentBrush`) и *свечение* (`neonGlow`), а тема решает:
 *
 * - Warm Paper — плоская заливка одним цветом, без тени. Бумага.
 * - ТУРБОВАЙБКОД — неоновый градиент и цветное свечение вокруг.
 *
 * Так шуточная тема не размазана по экранам: её правила живут в одном
 * месте, а экраны просто спрашивают «какой у тебя акцент?».
 */

/** Какая тема активна — читается из любого места дерева композиции. */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.WARM_PAPER }

/**
 * Неоновый градиент — одна кисть на всё приложение.
 *
 * Раньше `Brush.linearGradient` создавался на каждый вызов, то есть на
 * каждую строку очереди и на каждый кадр анимации. Кисть неизменяемая,
 * поэтому её можно создать один раз.
 */
private val BaybekodBrush by lazy { Brush.linearGradient(BaybekodGradient) }

/** Включена ли шуточная тема. */
@Composable
fun isBaybekod(): Boolean = LocalAppTheme.current == AppTheme.TURBOBAYBEKOD

/**
 * Основная заливка акцента: кнопки, плашки, линии.
 *
 * В бумаге это просто основной цвет. В ТУРБОВАЙБКОДе — неоновая радуга.
 */
@Composable
fun accentBrush(): Brush = if (isBaybekod()) {
    BaybekodBrush
} else {
    SolidColor(MaterialTheme.colorScheme.primary)
}

/**
 * Живая заливка: градиент медленно перетекает.
 *
 * Ровно то, чего хочется от темы, названной «вырвеглазный неон».
 */
@Composable
fun livingAccentBrush(): Brush {
    // В бумажной теме переход не нужен — иначе анимация крутилась бы
    // вхолостую, забирая кадры у интерфейса.
    if (!isBaybekod()) return SolidColor(MaterialTheme.colorScheme.primary)

    val shift by rememberInfiniteTransition(label = "neon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shift",
    )
    return Brush.linearGradient(
        colors = BaybekodGradient,
        start = Offset(shift * 1400f - 700f, 0f),
        end = Offset(shift * 1400f + 700f, 900f),
    )
}

/**
 * Свечение вокруг элемента.
 *
 * Цветная тень в Compose работает только на API 28+ — на старых версиях
 * она просто обычная серая тень, и это не страшно.
 */
@Composable
fun neonGlow(shape: Shape = RoundedCornerShape(2.dp)): Modifier = if (isBaybekod()) {
    Modifier.shadow(
        elevation = 16.dp,
        shape = shape,
        ambientColor = NeonGlow,
        spotColor = HotPinkNeon,
    )
} else {
    Modifier
}

/**
 * Текст с неоновым градиентом вместо однотонной заливки.
 *
 * `TextStyle.copy` требует сбросить цвет: если задан и цвет, и кисть,
 * приоритет у цвета — и градиент не будет виден.
 */
@Composable
fun gradientTextStyle(base: TextStyle): TextStyle = if (isBaybekod()) {
    // Два шага: у TextStyle отдельные перегрузки copy() — с цветом и с
    // кистью, но не с обоими сразу. Цвет сбрасываем в Unspecified,
    // иначе он перебьёт кисть и градиента не будет видно.
    base.copy(color = Color.Unspecified)
        .copy(brush = BaybekodBrush)
} else {
    base
}

/**
 * Эмодзи-украшение.
 *
 * В бумаге эмодзи нет — там тишина. В ТУРБОВАЙБКОДе каждый заголовок
 * должен кричать.
 */
@Composable
fun deco(emoji: String): String = if (isBaybekod()) "$emoji " else ""

/**
 * Подпись вкладки в нижней панели.
 *
 * Отдельный стиль, а не тематический `labelLarge`: у неоновой
 * типографики разрядка 1.5 sp, а при крупном системном шрифте (1.5)
 * она превращается в 2.25 sp на букву — и три подписи вкладок
 * перестают помещаться в экран, слипаясь и обрезаясь. Здесь разрядка
 * нулевая, а кегль чуть меньше.
 */
@Composable
fun tabLabelStyle(): TextStyle = if (isBaybekod()) {
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    )
} else {
    MaterialTheme.typography.labelLarge.copy(
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    )
}

/**
 * Кнопка акцента.
 *
 * Заменяет плоскую заливку: в бумаге это прямоугольник с чёрной
 * заливкой, в ТУРБОВАЙБКОДе — градиентная плашка со свечением.
 */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    living: Boolean = true,
) {
    val shape = RoundedCornerShape(if (isBaybekod()) 12.dp else 2.dp)
    Box(
        modifier = modifier
            .then(neonGlow(shape))
            .clip(shape)
            .background(if (living) livingAccentBrush() else accentBrush())
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (emoji != null) "${deco(emoji)}$text" else text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Прогресс-бар.
 *
 * Material-овский `LinearProgressIndicator` умеет только один цвет,
 * поэтому полоса рисуется вручную: трек — фон, заливка — неоновый
 * градиент. Высоту задаёт вызывающий код.
 */
@Composable
fun AccentProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safe = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    // Заливка рисуется кистью, а не вложенным Box'ом с `fillMaxWidth`:
    // изменение ширины заставляло Compose перемерить и переразложить
    // всю строку задачи — вместе с её текстом. Рисование ничего не
    // трогает: меняется только картинка.
    //
    // И без «живого» градиента: бесконечная анимация на каждой
    // качающейся строке гоняла перерисовку на 60 кадров в секунду.
    val fill = accentBrush()
    Box(
        modifier = modifier.drawBehind {
            drawRect(color = track)
            if (safe > 0f) {
                drawRect(brush = fill, size = Size(size.width * safe, size.height))
            }
        },
    )
}

/**
 * Разделительная линия.
 *
 * В бумаге — одна волосяная черта. В ТУРБОВАЙБКОДе — неоновая полоса
 * вдвое выше, чтобы интерфейс дрожал.
 */
@Composable
fun AccentDivider(modifier: Modifier = Modifier) {
    if (isBaybekod()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(2.dp)
                // Статичная кисть: разделитель рисуется после каждой
                // строки очереди, и анимация на каждом из них съедала бы
                // кадры интерфейса.
                .background(accentBrush()),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** Цвет неонового свечения (кислотный зелёный). */
val NeonGlow: Color get() = TurboBaybekod.colors.primary

/** Розовый неон — для второго слоя свечения. */
val HotPinkNeon: Color get() = TurboBaybekod.colors.primaryContainer
