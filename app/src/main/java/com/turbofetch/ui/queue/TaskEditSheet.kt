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

/**
 * Шторка редактирования одной задачи: качество, mp3 и имя файла.
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
    var audioOnly by remember(task.id) { mutableStateOf(task.audioOnly) }
    var customName by remember(task.id) { mutableStateOf(task.customName.orEmpty()) }

    val videoFormats = task.availableFormats.filter { it.hasVideo }
    val hasAudioFormat = task.availableFormats.any { it.isAudioOnly }

    val previewName = FileNameTemplate.render(
        template = template,
        title = task.title,
        author = task.author,
        quality = if (audioOnly) {
            "audio"
        } else {
            task.availableFormats.firstOrNull { it.id == selectedFormatId }?.label.orEmpty()
        },
        extension = if (audioOnly) "mp3" else "mp4",
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

            if (videoFormats.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Качество", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    videoFormats.forEach { format ->
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

            if (hasAudioFormat) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Только аудио (mp3)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Отдельный аудиопоток, без перекодирования",
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
                                selectedFormat = task.availableFormats
                                    .firstOrNull { it.id == selectedFormatId }
                                    ?: task.selectedFormat,
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
