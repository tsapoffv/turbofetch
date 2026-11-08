package com.turbofetch.ui.theme

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbofetch.domain.model.AppTheme

/**
 * «Акценты» — слой поверх темы.
 *
 * Экраны не знают, какая тема активна: они просят у темы *кисть*
 * (`accentBrush`) и *свечение* (`neonGlow`), а тема решает.
 *
 * В main-ветке тема одна и она плоская: бумага без неона и градиентов.
 * Слой оставлен потому, что в fun-ветке на нём стоит шуточная тема —
 * и там же живут те же самые компоненты, только с градиентом.
 */

/** Какая тема активна — читается из любого места дерева композиции. */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.WARM_PAPER }

/**
 * Включена ли шуточная тема.
 *
 * В main-ветке шуточной темы нет: она живёт только в fun-ветке.
 */
@Composable
fun isBaybekod(): Boolean = false

/** Основная заливка акцента: кнопки, плашки, линии. */
@Composable
fun accentBrush(): Brush = SolidColor(MaterialTheme.colorScheme.primary)

/** Заливка, которая перетекает. В бумаге она неподвижна. */
@Composable
fun livingAccentBrush(): Brush = SolidColor(MaterialTheme.colorScheme.primary)

/** Свечение вокруг элемента. В бумаге его нет. */
@Composable
fun neonGlow(shape: Shape = RoundedCornerShape(2.dp)): Modifier = Modifier

/** Текст с градиентом. В бумаге текст одноцветный. */
@Composable
fun gradientTextStyle(base: TextStyle): TextStyle = base

/** Эмодзи-украшение. В бумаге эмодзи нет — там тишина. */
@Composable
fun deco(emoji: String): String = ""

/**
 * Подпись вкладки в нижней панели.
 *
 * Отдельный стиль, а не тематический `labelLarge`: при крупном системном
 * шрифте (1.5) три подписи вкладок перестают помещаться в экран,
 * слипаясь и обрезаясь. Здесь разрядка нулевая, а кегль чуть меньше.
 */
@Composable
fun tabLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontSize = 12.sp,
    letterSpacing = 0.sp,
)

/**
 * Кнопка акцента: плоская плашка с прямой заливкой, без тени.
 */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    living: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary)
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
 * Прогресс-бар: волосяная полоса с одноцветной заливкой.
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
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.drawBehind {
            drawRect(color = track)
            if (safe > 0f) {
                drawRect(color = fill, size = Size(size.width * safe, size.height))
            }
        },
    )
}

/** Разделительная линия — одна волосяная черта. */
@Composable
fun AccentDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Цвет неонового свечения — в бумаге не используется. */
val NeonGlow: Color get() = Color.Transparent

/** Розовый неон — в бумаге не используется. */
val HotPinkNeon: Color get() = Color.Transparent
