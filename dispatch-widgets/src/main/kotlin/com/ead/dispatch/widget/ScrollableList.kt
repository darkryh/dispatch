package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.SegmentedSimplePlaceable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue

/**
 * A vertically scrollable list of items.
 *
 * Example:
 * ```kotlin
 * val items = listOf("Item 1", "Item 2", "Item 3", ...)
 * ScrollableList(
 *     items = items,
 *     modifier = Modifier.height(10),
 * ) { item ->
 *     Text(item)
 * }
 * ```
 *
 * @param items List of items to display.
 * @param modifier Modifiers to apply.
 * @param scrollState State holder for scroll position.
 * @param itemContent Composable content for each item.
 */
@Dispatchable
fun <T> ScrollableList(
    items: List<T>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    itemContent: @Dispatchable (T) -> Unit,
) {
    val composer = Composer.current
    val node = composer.startNode("ScrollableList")

    for (item in items) {
        itemContent(item)
    }

    val itemMeasurables = node.children

    val listMeasurable = ScrollableListMeasurable(
        modifier = modifier,
        itemMeasurables = itemMeasurables,
        scrollState = scrollState,
    )

    composer.registerMeasurable(listMeasurable)
    composer.endNode()
}

/**
 * Remember a scroll state.
 */
@Dispatchable
fun rememberScrollState(initialOffset: Int = 0): ScrollState {
    return remember { ScrollState(initialOffset) }
}

/**
 * State holder for scroll position.
 */
class ScrollState(initialOffset: Int = 0) {
    /**
     * Current scroll offset (in items/lines).
     */
    var offset: Int by mutableStateOf(initialOffset)
        internal set

    /**
     * Total content height (set during measurement).
     */
    var contentHeight: Int = 0
        internal set

    /**
     * Visible height (set during measurement).
     */
    var viewportHeight: Int = 0
        internal set

    /**
     * Maximum scroll offset.
     */
    val maxOffset: Int
        get() = (contentHeight - viewportHeight).coerceAtLeast(0)

    /**
     * Whether we can scroll up.
     */
    val canScrollUp: Boolean
        get() = offset > 0

    /**
     * Whether we can scroll down.
     */
    val canScrollDown: Boolean
        get() = offset < maxOffset

    /**
     * Scroll by a number of lines.
     */
    fun scrollBy(delta: Int) {
        offset = (offset + delta).coerceIn(0, maxOffset)
    }

    /**
    * Scroll by one page (viewport height).
    */
    fun scrollByPage(direction: Int) {
        val page = viewportHeight.coerceAtLeast(1)
        scrollBy(page * direction)
    }

    /**
     * Scroll to a specific offset.
     */
    fun scrollTo(newOffset: Int) {
        offset = newOffset.coerceIn(0, maxOffset)
    }

    /**
     * Scroll to the top.
     */
    fun scrollToTop() {
        offset = 0
    }

    /**
     * Scroll to the bottom.
     */
    fun scrollToBottom() {
        offset = maxOffset
    }

    /**
     * Scroll to make an index visible.
     */
    fun scrollToItem(index: Int, itemHeights: List<Int>) {
        if (index < 0 || itemHeights.isEmpty()) return

        // Calculate the Y position of the target item
        var targetY = 0
        for (i in 0 until index.coerceAtMost(itemHeights.size)) {
            targetY += itemHeights[i]
        }

        // If item is above viewport, scroll up to it
        if (targetY < offset) {
            offset = targetY
        }
        // If item is below viewport, scroll down to show it
        else if (targetY + itemHeights.getOrElse(index) { 1 } > offset + viewportHeight) {
            offset = targetY + itemHeights.getOrElse(index) { 1 } - viewportHeight
        }

        offset = offset.coerceIn(0, maxOffset)
    }
}

/**
 * Measurable for ScrollableList.
 */
internal class ScrollableListMeasurable(
    override val modifier: Modifier,
    private val itemMeasurables: List<Measurable>,
    private val scrollState: ScrollState,
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        // Check if height is unbounded (for terminal scrolling mode)
        val isUnbounded = !modifiedConstraints.hasBoundedHeight ||
                          modifiedConstraints.maxHeight == Int.MAX_VALUE

        if (isUnbounded) {
            // UNBOUNDED MODE: Measure all items, return full content (no virtualization)
            // Terminal scrollback will handle scrolling
            return measureUnbounded(modifiedConstraints)
        }

        // BOUNDED MODE: Original virtualization logic
        val viewportHeight = modifiedConstraints.maxHeight

        // Virtualize: measure only enough items to fill viewport plus small buffer
        val itemConstraints = Constraints(
            minWidth = 0,
            maxWidth = modifiedConstraints.maxWidth,
            minHeight = 0,
            maxHeight = viewportHeight,
        )

        val itemHeights = mutableListOf<Int>()
        val itemPlaceables = mutableListOf<Placeable>()
        var measuredHeight = 0

        for (measurable in itemMeasurables) {
            // Apply size modifiers at the parent level so layout-based measurables (e.g. Spacer/Box/Row/Column)
            // respect constraints like `Modifier.height(...)`.
            val placeable = measurable.measure(measurable.modifier.applyToConstraints(itemConstraints))
            itemPlaceables.add(placeable)
            itemHeights.add(placeable.height)
            measuredHeight += placeable.height
            if (measuredHeight >= viewportHeight * 2) break // simple buffer to reduce work
        }

        // Calculate total content height (requires full heights; fall back to measured slice if partial)
        val totalContentHeight = if (itemPlaceables.size == itemMeasurables.size) {
            itemPlaceables.sumOf { it.height }
        } else {
            // Estimate using average height of measured items
            val avg = itemHeights.average().toInt().coerceAtLeast(1)
            avg * itemMeasurables.size
        }

        // Update scroll state
        scrollState.contentHeight = totalContentHeight
        scrollState.viewportHeight = viewportHeight
        // Clamp stale offsets when content shrinks (e.g. transient status rows disappear).
        // Without this, rendering can start past the end and leave the viewport partially blank.
        scrollState.offset = scrollState.offset.coerceIn(0, scrollState.maxOffset)

        // Determine visible range using offset and viewport height; avoid work outside viewport
        val scrollOffset = scrollState.offset
        val visibleLines = mutableListOf<String>()
        var y = 0
        for (placeable in itemPlaceables) {
            val itemStart = y
            val itemEnd = y + placeable.height

            if (itemEnd > scrollOffset && itemStart < scrollOffset + viewportHeight) {
                val startLine = maxOf(0, scrollOffset - itemStart)
                val endLine = (scrollOffset + viewportHeight - itemStart).coerceAtMost(placeable.height)
                for (lineIndex in startLine until endLine) {
                    if (visibleLines.size < viewportHeight) {
                        visibleLines.add(placeable.lines.getOrElse(lineIndex) { "" })
                    }
                }
            }
            y += placeable.height
            if (visibleLines.size >= viewportHeight) break
        }

        // Pad to viewport height if needed
        val width = modifiedConstraints.maxWidth.takeIf { it != Int.MAX_VALUE }
            ?: visibleLines.maxOfOrNull { it.length }
            ?: 0

        while (visibleLines.size < viewportHeight) {
            visibleLines.add(" ".repeat(width))
        }

        return SimplePlaceable(
            width = width,
            height = viewportHeight,
            lines = visibleLines,
        )
    }

    /**
     * Measure all items without virtualization (for unbounded/terminal scrolling mode).
     */
    private fun measureUnbounded(constraints: Constraints): Placeable {
        // Measure ALL items with unbounded height
        val itemConstraints = Constraints(
            minWidth = 0,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = Int.MAX_VALUE,
        )

        val itemPlaceables = itemMeasurables.map { it.measure(it.modifier.applyToConstraints(itemConstraints)) }
        val segmentHeights = itemPlaceables.map { it.height }

        // Collect ALL lines (no offset clipping)
        val allLines = mutableListOf<String>()
        for (placeable in itemPlaceables) {
            allLines.addAll(placeable.lines)
        }

        // Update scroll state metadata
        scrollState.contentHeight = allLines.size
        scrollState.viewportHeight = allLines.size
        scrollState.offset = scrollState.offset.coerceIn(0, scrollState.maxOffset)

        val width = constraints.maxWidth.takeIf { it != Int.MAX_VALUE }
            ?: allLines.maxOfOrNull { it.length }
            ?: 0

        return SegmentedSimplePlaceable(
            width = width,
            height = allLines.size, // Natural height
            lines = allLines,
            segmentHeights = segmentHeights,
        )
    }
}

/**
 * A scrollable column with indicator.
 */
@Dispatchable
fun <T> ScrollableListWithIndicator(
    items: List<T>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    showScrollbar: Boolean = true,
    itemContent: @Dispatchable (T) -> Unit,
) {
    Row(modifier = modifier) {
        ScrollableList(
            items = items,
            modifier = Modifier.weight(1f),
            scrollState = scrollState,
            itemContent = itemContent,
        )

        if (showScrollbar) {
            Scrollbar(scrollState)
        }
    }
}

/**
 * A vertical scrollbar.
 */
@Dispatchable
private fun Scrollbar(scrollState: ScrollState) {
    val composer = Composer.current
    composer.startNode("Scrollbar")

    val measurable = ScrollbarMeasurable(scrollState)
    composer.registerMeasurable(measurable)

    composer.endNode()
}

internal class ScrollbarMeasurable(
    private val scrollState: ScrollState,
) : Measurable {
    override val modifier: Modifier = Modifier

    override fun measure(constraints: Constraints): Placeable {
        val height = constraints.maxHeight.takeIf { it != Int.MAX_VALUE } ?: 1

        val lines = mutableListOf<String>()

        if (scrollState.contentHeight <= scrollState.viewportHeight) {
            // No scrolling needed - show empty track
            repeat(height) { lines.add("│") }
        } else {
            // Calculate thumb position and size
            val ratio = height.toFloat() / scrollState.contentHeight
            val thumbSize = (scrollState.viewportHeight * ratio).toInt().coerceIn(1, height)
            val thumbPosition = ((scrollState.offset * ratio).toInt()).coerceIn(0, height - thumbSize)

            for (i in 0 until height) {
                if (i >= thumbPosition && i < thumbPosition + thumbSize) {
                    lines.add("█")
                } else {
                    lines.add("░")
                }
            }
        }

        return SimplePlaceable(
            width = 1,
            height = height,
            lines = lines,
        )
    }
}
