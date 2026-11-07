package com.turbofetch.ui.shrine

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turbofetch.R
import com.turbofetch.ui.theme.AccentButton
import com.turbofetch.ui.theme.AccentDivider
import com.turbofetch.ui.theme.deco
import com.turbofetch.ui.theme.gradientTextStyle
import com.turbofetch.ui.theme.isBaybekod
import com.turbofetch.ui.theme.livingAccentBrush
import com.turbofetch.ui.theme.neonGlow

/**
 * «Святилище Питера Тиля».
 *
 * Фотография, а под ней — цитаты, которые генерируются цепью Маркова
 * из настоящих реплик Тиля. Ни одна из них не была сказана им:
 * цепь склеивает реальные слова в правдоподобную, но бессмысленную вязь.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShrineScreen(
    viewModel: ShrineViewModel,
    modifier: Modifier = Modifier,
) {
    // Цитата дня готовится один раз за сеанс: список откровений пустой
    // до нажатия «Открыть ворота».
    LaunchedEffect(Unit) { viewModel.prepareQuoteOfTheDay() }

    val topic = viewModel.topic
    val quotes = viewModel.revelationsFor(topic)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        // Фотография в «раме» — тонкая волосяная линия, как на бумаге.
        Image(
            painter = painterResource(R.drawable.peter_thiel),
            contentDescription = "Питер Тиль",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(neonGlow(RoundedCornerShape(if (isBaybekod()) 16.dp else 2.dp)))
                .clip(RoundedCornerShape(if (isBaybekod()) 16.dp else 2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "${deco("🕶️")}Питер Тиль",
            style = gradientTextStyle(MaterialTheme.typography.displayMedium),
            color = if (isBaybekod()) {
                androidx.compose.ui.graphics.Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${deco("⛪")}Святилище №1 · откровения генерируются цепью Маркова",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        AccentDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            text = "${deco("🗂️")}Темы",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShrineTopic.entries.forEach { candidate ->
                val selected = candidate == topic
                val shape = RoundedCornerShape(if (isBaybekod()) 20.dp else 2.dp)
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .then(if (selected) neonGlow(shape) else Modifier)
                        .clip(shape)
                        .background(
                            if (selected) {
                                livingAccentBrush()
                            } else {
                                androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.surface,
                                )
                            },
                        )
                        .clickable { viewModel.selectTopic(candidate) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = topic.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        AccentDivider()
        Spacer(Modifier.height(20.dp))

        // --- Цитата дня ---
        Text(
            text = "${deco("🔥")}Цитата дня",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        viewModel.quoteOfTheDay?.let { revelation ->
            QuoteBlock(revelation, prominent = true)
        }

        Spacer(Modifier.height(16.dp))

        // Кнопка под цитатой дня — как и просили: сначала дельца дня,
        // потом кнопка, а уже под ней список откровений.
        AccentButton(
            text = "Открыть ворота",
            emoji = "🌀",
            onClick = { viewModel.reveal() },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        AccentDivider()
        Spacer(Modifier.height(20.dp))

        // --- Список откровений ---
        Text(
            text = "${deco("👁️")}Откровения",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${quotes.size} из ${MAX_QUOTES} · выдано ${viewModel.revealed}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (quotes.isEmpty()) {
            Text(
                text = "${deco("🤫")}Пока тишина. Нажмите «Открыть ворота».",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            quotes.forEach { revelation ->
                QuoteBlock(revelation)
                Spacer(Modifier.height(18.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        AccentDivider()
        Spacer(Modifier.height(14.dp))

        Text(
            text = "Фото: Dan Taylor / Wikimedia Commons, CC BY 2.0. " +
                "Корпус цитат: «Zero to One» (2014), эссе «The Education of a " +
                "Libertarian» (2009) и публичные лекции. Цитаты на экране " +
                "составлены цепью Маркова и не принадлежат Тилю.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Откровение: цитата, а под ней — мелким шрифтом тема, из которой она
 * собрана. Тонкая линия слева — как сноска на полях.
 */
@Composable
private fun QuoteBlock(revelation: Revelation, prominent: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(if (prominent) 3.dp else 2.dp)
                .height(if (prominent) 84.dp else 64.dp)
                .background(
                    if (prominent) {
                        livingAccentBrush()
                    } else {
                        androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outline,
                        )
                    },
                ),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "«${revelation.text}»",
                style = if (prominent) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = revelation.topic.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Сколько откровений помещается в список. */
private const val MAX_QUOTES = 5
