package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.modifier.Modifier
import kotlin.math.max
import kotlin.math.min

@Dispatchable
fun <T> SelectableWindowedList(
    items: List<T>,
    selectedIndex: Int,
    visibleCount: Int,
    styles: SelectableListStyles,
    modifier: Modifier = Modifier,
    itemSpacing: Int = 0,
    itemContent: @Dispatchable (item: T, isSelected: Boolean) -> Unit,
) {
    if (items.isEmpty()) return

    val slice = computeWindowSlice(items, selectedIndex, visibleCount)
    if (slice.window.isEmpty()) return

    SelectableList(
        items = slice.window,
        selectedIndex = slice.localSelected,
        styles = styles,
        modifier = modifier,
        itemSpacing = itemSpacing,
        itemContent = itemContent,
    )
}

internal data class WindowSlice<T>(
    val window: List<T>,
    val localSelected: Int,
    val startIndex: Int,
    val endIndex: Int,
)

internal fun <T> computeWindowSlice(
    items: List<T>,
    selectedIndex: Int,
    visibleCount: Int,
): WindowSlice<T> {
    if (items.isEmpty()) return WindowSlice(emptyList(), 0, 0, 0)

    val safeCount = visibleCount.coerceAtLeast(1)
    val boundedSelected = selectedIndex.coerceIn(0, items.lastIndex)
    val startIndex = max(0, boundedSelected - safeCount + 1)
    val endIndex = min(items.size, startIndex + safeCount)
    val window = items.subList(startIndex, endIndex)
    val localSelected = (boundedSelected - startIndex).coerceIn(0, window.lastIndex)

    return WindowSlice(window, localSelected, startIndex, endIndex)
}
