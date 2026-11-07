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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turbofetch.R

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
    // При первом показе подставляем несколько цитат, чтобы экран не пустовал.
    LaunchedEffect(Unit) { viewModel.prime(3) }

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
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Питер Тиль",
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Святилище №1 · откровения генерируются цепью Маркова",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Темы",
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
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Откровения",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${quotes.size} всего · выдано ${viewModel.revealed}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (quotes.isEmpty()) {
            Text(
                text = "Пока тишина. Нажмите «Открыть ворота».",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            quotes.forEach { revelation ->
                QuoteBlock(revelation)
                Spacer(Modifier.height(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Кнопка в стиле бумаги: чёрная плашка, прямые углы.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { viewModel.reveal() }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Открыть ворота",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun QuoteBlock(revelation: Revelation) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(64.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "«${revelation.text}»",
                style = MaterialTheme.typography.bodyLarge,
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
