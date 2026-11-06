package com.turbofetch.ui.queue

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Перетаскивание элементов списка.
 *
 * Логика: запоминаем, какой элемент схватили и на сколько сместился палец.
 * Затем смотрим, над каким элементом оказался перетаскиваемый, и просим
 * список поменять их местами. Смещение корректируем так, чтобы карточка
 * осталась под пальцем.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {

    /** Индекс перетаскиваемого элемента, `null` — ничего не тащим. */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    /** На сколько смещён перетаскиваемый элемент относительно своего места. */
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(index: Int) {
        draggingItemIndex = index
        dragOffset = 0f
    }

    fun onDrag(delta: Float) {
        val index = draggingItemIndex ?: return
        dragOffset += delta

        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return

        val center = (dragged.offset + dragOffset + dragged.size / 2).toInt()
        val target = layout.visibleItemsInfo.firstOrNull { item ->
            item.index != index && center in (item.offset..(item.offset + item.size))
        } ?: return

        onMove(index, target.index)

        // Элемент занял место цели — сдвигаем смещение, чтобы он остался под пальцем.
        dragOffset += dragged.offset - target.offset
        draggingItemIndex = target.index
    }

    fun onDragEnd() {
        draggingItemIndex = null
        dragOffset = 0f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderState = remember(listState) { ReorderState(listState, onMove) }

/**
 * Модификатор для ручки перетаскивания: жест на самой ручке, а не на всей
 * карточке, — иначе перетаскивание конфликтует с прокруткой списка.
 */
fun Modifier.dragHandle(
    state: ReorderState,
    index: Int,
): Modifier = this.pointerInput(index) {
    detectDragGestures(
        onDragStart = { state.onDragStart(index) },
        onDrag = { change, amount ->
            change.consume()
            state.onDrag(amount.y)
        },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() },
    )
}
