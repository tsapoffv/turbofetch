package com.turbofetch.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.domain.model.Source
import com.turbofetch.domain.model.TaskStatus
import com.turbofetch.ui.theme.AccentButton
import com.turbofetch.ui.theme.AccentDivider
import com.turbofetch.ui.theme.AccentProgress
import com.turbofetch.ui.theme.deco
import com.turbofetch.ui.theme.gradientTextStyle
import com.turbofetch.ui.theme.isBaybekod
import com.turbofetch.ui.theme.livingAccentBrush
import com.turbofetch.ui.theme.neonGlow

/**
 * Очередь загрузок — «Warm Paper»: без карточек и теней, строки
 * разделяются тонкой линией, кнопки плоские и прямые.
 */
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    fileNameTemplate: String,
    modifier: Modifier = Modifier,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<DownloadTask?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderState(listState) { from, to -> viewModel.moveTask(from, to) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Wordmark(
            taskCount = tasks.size,
            onClearAll = { viewModel.clearQueue() },
        )
        LinkInput(onSubmit = { link -> viewModel.addLink(link) })

        Box(modifier = Modifier.weight(1f)) {
            if (tasks.isEmpty()) {
                EmptyQueue()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        // Запас под кнопку «Скачать всё»: без него
                        // она накрывает последнюю задачу в списке.
                        bottom = 112.dp,
                    ),
                ) {
                    itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                        val dragging = reorderState.draggingItemIndex == index
                        TaskRow(
                            task = task,
                            dragging = dragging,
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    if (dragging) {
                                        translationY = reorderState.dragOffset
                                        shadowElevation = 16.dp.toPx()
                                    }
                                }
                                // Перетаскиваемую строку анимировать нельзя:
                                // её позиция и так задаётся сдвигом, а animateItem
                                // двигал бы её второй раз — строка «дёргалась».
                                .then(if (dragging) Modifier else Modifier.animateItem()),
                            dragHandleModifier = Modifier.dragHandle(reorderState, index),
                            onClick = { editing = task },
                            onDownload = { viewModel.download(task.id) },
                            onCancel = { viewModel.cancel(task.id) },
                            onDelete = { viewModel.delete(task.id) },
                            onRetry = { viewModel.retry(task.id) },
                            onOpen = { viewModel.openOutput(task) },
                        )
                        AccentDivider()
                    }
                }
            }

            if (tasks.any { it.status == TaskStatus.READY || it.status == TaskStatus.PENDING }) {
                AccentButton(
                    text = "Скачать всё",
                    emoji = "⬇️",
                    onClick = { viewModel.downloadAll() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }

        SnackbarHost(snackbarHostState)
    }

    editing?.let { task ->
        TaskEditSheet(
            task = task,
            template = fileNameTemplate,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.saveTask(updated)
                editing = null
            },
        )
    }
}

/**
 * Логотип: антиква, как заголовок в журнале. Над ним — тонкая линия,
 * как правило на полях.
 */
@Composable
private fun Wordmark(
    taskCount: Int,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${deco("⚡")}TurboFetch",
                style = gradientTextStyle(MaterialTheme.typography.displayMedium),
                color = if (isBaybekod()) {
                    androidx.compose.ui.graphics.Color.Unspecified
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (taskCount > 0) {
                Text(
                    text = "Очистить всё",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onClearAll() }
                        .padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (taskCount > 0) {
                "${deco("📦")}Загрузка напрямую с CDN · в очереди $taskCount"
            } else {
                "${deco("📦")}Загрузка напрямую с CDN"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        AccentDivider()
    }
}

/** Плоская кнопка-«плашка»: чёрная заливка, прямые углы. */
@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccentButton(text = text, onClick = onClick, modifier = modifier, living = false)
}

/** Поле для ввода ссылки вручную — «Поделиться» не единственный способ. */
@Composable
private fun LinkInput(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(if (isBaybekod()) 12.dp else 2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "${deco("🔗")}Вставьте ссылку на видео",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        val sendShape = RoundedCornerShape(if (isBaybekod()) 12.dp else 2.dp)
        Box(
            modifier = Modifier
                .size(46.dp)
                .then(neonGlow(sendShape))
                .clip(sendShape)
                .background(
                    if (text.isNotBlank()) {
                        livingAccentBrush()
                    } else {
                        androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.surfaceVariant,
                        )
                    },
                )
                .clickable {
                    val link = text.trim()
                    if (link.isNotEmpty()) {
                        onSubmit(link)
                        text = ""
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Добавить в очередь",
                tint = if (text.isNotBlank()) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyQueue() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = if (isBaybekod()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "${deco("🕸️")}Очередь пуста",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Поделитесь ссылкой из YouTube, TikTok или X — она появится здесь.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    dragging: Boolean = false,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (dragging) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Перетащить",
                tint = MaterialTheme.colorScheme.outline,
                modifier = dragHandleModifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            task.thumbnailUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(task.source.label())
                        task.selectedFormat?.let { append(" · ${it.label}") }
                        if (task.audioOnly) append(" · mp3")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete),
            )
        }

        when (task.status) {
            TaskStatus.RUNNING, TaskStatus.PROBING -> {
                Spacer(Modifier.height(12.dp))
                AccentProgress(
                    progress = task.progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton2("Отменить", onCancel)
                    if (task.status == TaskStatus.RUNNING) {
                        Text(
                            "${(task.progress * 100).toInt()} %",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TaskStatus.FAILED -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = task.errorMessage ?: "Ошибка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton2("Повторить", onRetry)
                    TextButton2("Удалить", onDelete)
                }
            }

            TaskStatus.COMPLETED -> {
                Spacer(Modifier.height(10.dp))
                TextButton2("Открыть", onOpen)
            }

            else -> {
                Spacer(Modifier.height(10.dp))
                TextButton2("Скачать", onDownload)
            }
        }
    }
}

/** Текстовая кнопка без заливки — как подчёркнутая ссылка. */
@Composable
private fun TextButton2(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    )
}

private fun Source.label(): String = when (this) {
    Source.YOUTUBE -> "YouTube"
    Source.TIKTOK -> "TikTok"
    Source.X -> "X"
    Source.UNKNOWN -> "Ссылка"
}
