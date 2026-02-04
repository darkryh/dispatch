package com.ead.dispatch.sample.presentation.library

fun calculateVisibleCount(
    terminalHeight: Int,
    reservedLines: Int,
    itemLines: Int,
    itemSpacing: Int,
): Int {
    val available = (terminalHeight - reservedLines).coerceAtLeast(itemLines)
    val itemBlock = itemLines + itemSpacing
    return ((available + itemSpacing) / itemBlock).coerceAtLeast(1)
}
