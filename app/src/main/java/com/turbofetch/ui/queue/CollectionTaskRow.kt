package com.turbofetch.ui.queue

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.turbofetch.domain.model.CollectionTrack
import com.turbofetch.domain.model.DownloadTask
import com.turbofetch.ui.theme.AccentButton
import com.turbofetch.ui.theme.accentBrush

/**
 * Карточка альбома или плейлиста.
 *
 * Отличается от обычной задачи тем, что качать здесь нечего: сначала юзер
 * выбирает треки, и только потом они становятся отдельными задачами.
 *
 * Обложки: у альбома — своя одна, у плейлиста — три обложки треков в ряд.
 * Список треков отодвинут вправо и отделён вертикальной полоской — видно,
 * что треки принадлежат этому альбому.
 */
@Composable
fun CollectionTaskRow(
    task: DownloadTask,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleTrack: (String) -> Unit,
    onDownloadSelected: () -> Unit,
    onDelete: () -> Unit,
) {
    val tracks = task.collectionTracks
    // Производные значения считаются один раз на набор треков: раньше
    // они пересчитывались при каждой перерисовке карточки.
    val selectedCount = remember(tracks) { tracks.count { it.selected } }
    val covers = remember(tracks) { tracks.mapNotNull { it.thumbUrl }.distinct().take(3) }
    val totalLabel = remember(tracks) { formatTotalDuration(tracks) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (covers.isNotEmpty()) {
                CollectionCovers(covers)
            } else {
                task.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${task.source.label()} · ${tracks.size} треков",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Длительность — отдельной строкой: в одной строке с числом
                // треков она обрезалась бы («2 ч 44 …»).
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            CollectionTrackList(
                tracks = tracks,
                onToggle = onToggleTrack,
            )
            Spacer(Modifier.height(12.dp))
            AccentButton(
                text = "Скачать выбранное ($selectedCount)",
                emoji = "⬇️",
                onClick = onDownloadSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Три обложки треков в ряд.
 *
 * Крупные, с небольшим нахлёстом и лёгким наклоном — ряд читается как
 * «много обложек», а не как каша.
 */
@Composable
private fun CollectionCovers(covers: List<String>) {
    val rotations = listOf(-7f, 2f, 8f)
    Box(
        modifier = Modifier
            .width(132.dp)
            .height(80.dp),
    ) {
        covers.forEachIndexed { index, url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = (index * 30).dp, y = (index * 2).dp)
                    .size(72.dp)
                    .graphicsLayer { rotationZ = rotations.getOrElse(index) { 0f } }
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            )
        }
    }
}

/**
 * Список треков с отметками.
 *
 * Отодвинут вправо и отделён от родителя вертикальной полоской: треки
 * принадлежат альбому, а не висят в общем списке.
 */
@Composable
private fun CollectionTrackList(
    tracks: List<CollectionTrack>,
    onToggle: (String) -> Unit,
) {
    // Полоска рисуется, а не занимает место в разметке: `IntrinsicSize.Min`
    // заставлял Compose измерять содержимое дважды.
    val stripeBrush = accentBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = stripeBrush,
                    size = Size(3.dp.toPx(), size.height),
                )
            },
    ) {
        Spacer(Modifier.width(15.dp))
        // Список треков — ленивый: плейлист на сто треков это сто
        // картинок и сто галочек, и в обычной колонке они все создавались
        // разом. Высота ограничена, чтобы вложенный список не съедал
        // весь экран.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 320.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                CollectionTrackItem(track = track, onToggle = onToggle)
            }
        }
    }
}

/** Одна строка списка треков. */
@Composable
private fun CollectionTrackItem(
    track: CollectionTrack,
    onToggle: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(track.id) }
            .padding(vertical = 2.dp),
    ) {
        Checkbox(
            checked = track.selected,
            onCheckedChange = { onToggle(track.id) },
        )
        track.thumbUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (track.selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(track.durationSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * «3:33».
 *
 * Строка собирается вручную: `String.format` создаёт `Formatter` и ходит
 * в локаль на каждый вызов, а эта функция зовётся на каждый трек.
 */
private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val minutes = seconds / 60
    val rest = seconds % 60
    return buildString {
        append(minutes)
        append(':')
        if (rest < 10) append('0')
        append(rest)
    }
}

/** «34 мин» для суммарной длительности. */
private fun formatTotalDuration(tracks: List<CollectionTrack>): String {
    val total = tracks.sumOf { it.durationSeconds }
    if (total <= 0) return "—"
    val minutes = total / 60
    return if (minutes >= 60) {
        "${minutes / 60} ч ${minutes % 60} мин"
    } else {
        "$minutes мин"
    }
}
