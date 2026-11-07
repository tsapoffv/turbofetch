package com.turbofetch.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.FileNameTemplate
import com.turbofetch.domain.model.MediaNaming

/**
 * Шторка редактирования одной задачи: качество, звук, mp3 и имя файла.
 * Имя пересчитывается по шаблону из настроек на лету — так видно, что получится.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditSheet(
    task: DownloadTask,
    template: String,
    onDismiss: () -> Unit,
    onSave: (DownloadTask) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFormatId by remember(task.id) { mutableStateOf(task.selectedFormat?.id) }
    var selectedAudioId by remember(task.id) { mutableStateOf(task.audioFormat?.id) }
    var audioOnly by remember(task.id) { mutableStateOf(task.audioOnly) }
    var customName by remember(task.id) { mutableStateOf(task.customName.orEmpty()) }

    val videoFormats = task.availableFormats.filter { it.hasVideo && it.hasAudio }
    val videoOnlyFormats = task.availableFormats.filter { it.hasVideo && !it.hasAudio }
    val audioFormats = task.availableFormats.filter { it.hasAudio && !it.hasVideo }

    // Все варианты видео: и со звуком, и требующие отдельной аудиодорожки.
    val allVideoFormats = videoFormats + videoOnlyFormats
    val selectedFormat = allVideoFormats.firstOrNull { it.id == selectedFormatId }
    val needsSeparateAudio = selectedFormat != null && !selectedFormat.hasAudio

    // Источник без видео (SoundCloud): площадка сама отдаёт звук, и мы
    // сохраняем его как есть — m4a. mp3 появляется только по запросу.
    val isAudioSource = MediaNaming.isAudioOnlySource(task.availableFormats)
    val container = MediaNaming.container(task.availableFormats, audioOnly)

    val previewName = FileNameTemplate.render(
        template = template,
        title = task.title,
        author = task.author,
        // Для площадок, отдающих только звук, в имя идёт подпись потока
        // («160 кбит/с») — так же, как считает воркер. Слово «audio» остаётся
        // для YouTube, где звук вытаскивается из видео.
        quality = when {
            isAudioSource -> task.availableFormats.firstOrNull()?.label.orEmpty()
            audioOnly -> "audio"
            else -> selectedFormat?.label.orEmpty()
        },
        extension = MediaNaming.extension(container),
        id = task.id,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Параметры загрузки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                task.displayTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (allVideoFormats.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Качество", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allVideoFormats.forEach { format ->
                        FilterChip(
                            selected = !audioOnly && selectedFormatId == format.id,
                            onClick = {
                                selectedFormatId = format.id
                                audioOnly = false
                            },
                            label = { Text(format.label) },
                        )
                    }
                }
            }

            if (needsSeparateAudio && audioFormats.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Качество звука", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Видео и звук приходят отдельными потоками — они будут склеены в один файл",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioFormats.forEach { format ->
                        FilterChip(
                            selected = selectedAudioId == format.id,
                            onClick = { selectedAudioId = format.id },
                            label = { Text(format.label) },
                        )
                    }
                }
            }

            if (audioFormats.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isAudioSource) {
                                "Конвертировать в mp3"
                            } else {
                                "Только аудио (mp3)"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (isAudioSource) {
                                "По умолчанию сохраняем как M4A — без перекодирования"
                            } else {
                                "Отдельный аудиопоток, без перекодирования"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = audioOnly, onCheckedChange = { audioOnly = it })
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Своё имя (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Итоговое имя: ${customName.ifBlank { previewName }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(
                    onClick = {
                        onSave(
                            task.copy(
                                selectedFormat = allVideoFormats
                                    .firstOrNull { it.id == selectedFormatId }
                                    ?: task.selectedFormat,
                                audioFormat = audioFormats
                                    .firstOrNull { it.id == selectedAudioId }
                                    ?: task.audioFormat,
                                audioOnly = audioOnly,
                                customName = customName.ifBlank { null },
                            ),
                        )
                    },
                ) { Text("Сохранить") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
