package com.turbofetch.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turbofetch.domain.model.DownloadedFile
import com.turbofetch.ui.theme.AccentButton
import com.turbofetch.ui.theme.AccentDivider
import com.turbofetch.ui.theme.deco
import com.turbofetch.ui.theme.gradientTextStyle
import com.turbofetch.ui.theme.isBaybekod

/**
 * Скачанные файлы: второй список рядом с очередью.
 *
 * Здесь то, что уже лежит в галерее: можно открыть файл, удалить один
 * или убрать всё сразу.
 */
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    modifier: Modifier = Modifier,
) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${deco("📁")}Скачанное",
                style = gradientTextStyle(MaterialTheme.typography.displayMedium),
                color = if (isBaybekod()) {
                    androidx.compose.ui.graphics.Color.Unspecified
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (files.isEmpty()) {
                    "${deco("💾")}Здесь появятся файлы после загрузки"
                } else {
                    "${deco("💾")}${files.size} ${pluralFiles(files.size)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            AccentDivider()
        }

        Column(modifier = Modifier.weight(1f)) {
            if (files.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${deco("🫙")}Пока пусто",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Скачанные файлы останутся здесь, даже если удалить их из очереди.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            onOpen = {
                                val uri = android.net.Uri.parse(file.uri)
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    .setDataAndType(uri, file.mimeType)
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            },
                            onDelete = { viewModel.delete(file) },
                        )
                        AccentDivider()
                    }
                }
            }

        }

        // Кнопка отдельной строкой под списком: накладывать её поверх
        // списка нельзя — она закрывала последний файл.
        if (files.isNotEmpty()) {
            AccentButton(
                text = "Удалить всё",
                emoji = "💀",
                onClick = { viewModel.deleteAll() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun FileRow(
    file: DownloadedFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${file.fileName} · ${file.sizeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        // Иконки фиксированной ширины: при крупном шрифте текст не должен
        // отжимать их и ломать строку.
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Открыть",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Удалить",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onDelete),
        )
    }
}

/** «1 файл», «2 файла», «5 файлов» — без ошибок в склонениях. */
private fun pluralFiles(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "файлов"
        mod10 == 1 -> "файл"
        mod10 in 2..4 -> "файла"
        else -> "файлов"
    }
}
