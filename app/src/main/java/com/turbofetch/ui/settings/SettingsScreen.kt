package com.turbofetch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turbofetch.domain.model.AppTheme
import com.turbofetch.domain.model.FileNameTemplate
import com.turbofetch.domain.model.QualityPreset
import com.turbofetch.ui.theme.deco
import com.turbofetch.ui.theme.isBaybekod
import com.turbofetch.ui.theme.livingAccentBrush
import com.turbofetch.ui.theme.neonGlow

/**
 * Настройки — в том же «бумажном» стиле: чипы без заливки,
 * разделители-линии, никаких карточек.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        // MaterialTheme по умолчанию даёт чёрный LocalContentColor —
        // на тёмной теме текст был бы невидим.
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle("Тема")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        selected = settings.theme == theme,
                        dark = settings.darkMode,
                        onSelect = { viewModel.update { it.copy(theme = theme) } },
                        onToggleDark = { viewModel.update { it.copy(darkMode = !it.darkMode) } },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            SectionTitle("Качество по умолчанию")
            Text(
                "${deco("🎚️")}Применяется к новым задачам в очереди",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityPreset.entries.forEach { preset ->
                    val selected = settings.defaultQuality == preset
                    val shape = RoundedCornerShape(if (isBaybekod()) 20.dp else 2.dp)
                    Text(
                        text = preset.label,
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
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                },
                            )
                            .clickable { viewModel.update { it.copy(defaultQuality = preset) } }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            SectionTitle("Имя файла")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = settings.fileNameTemplate,
                onValueChange = { value ->
                    viewModel.update { it.copy(fileNameTemplate = value) }
                },
                label = { Text("Шаблон имени") },
                supportingText = {
                    Text("Плейсхолдеры: {title} {author} {quality} {ext} {date} {id}")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Пример: " + FileNameTemplate.render(
                    template = settings.fileNameTemplate,
                    title = "Мой ролик",
                    author = "Автор",
                    quality = "1080p",
                    extension = "mp4",
                    id = "abc123",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            SwitchRow(
                title = "Только аудио по умолчанию",
                subtitle = "Новые задачи скачиваются как mp3",
                checked = settings.defaultAudioOnly,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(defaultAudioOnly = value) }
                },
            )
            SwitchRow(
                title = "Открывать после скачивания",
                subtitle = "Запускать системный плеер, когда файл готов",
                checked = settings.openAfterDownload,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(openAfterDownload = value) }
                },
            )
            SwitchRow(
                title = "Только по Wi-Fi",
                subtitle = "Не качать через мобильный интернет",
                checked = settings.downloadOverWifiOnly,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(downloadOverWifiOnly = value) }
                },
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/**
 * Строка выбора темы: название, пояснение и переключатель тёмного
 * варианта. У ТУРБОВАЙБКОДА тёмного варианта нет — там переключатель
 * не показывается.
 */
@Composable
private fun ThemeRow(
    theme: AppTheme,
    selected: Boolean,
    dark: Boolean,
    onSelect: () -> Unit,
    onToggleDark: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    neonGlow(RoundedCornerShape(if (isBaybekod()) 14.dp else 2.dp))
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(if (isBaybekod()) 14.dp else 2.dp))
            .background(
                if (selected) {
                    if (isBaybekod()) {
                        livingAccentBrush()
                    } else {
                        androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                } else {
                    androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.background,
                    )
                },
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    if (isBaybekod()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected && isBaybekod()) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        if (theme.hasDarkVariant) {
            Text(
                text = if (dark) "🌙 Тёмная" else "☀️ Светлая",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = dark, onCheckedChange = { onToggleDark() })
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
