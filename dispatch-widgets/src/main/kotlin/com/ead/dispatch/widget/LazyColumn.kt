package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.composableContainer

/** A keyed, viewport-composed vertical list for terminal content. */
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    stickToEnd: Boolean = false,
    content: @Composable LazyListScope.() -> Unit,
) {
    val scope = LazyListScopeImpl()
    scope.content()
    val cache = remember { LazyItemHeightCache() }
    val viewportHeight = LocalTerminalHeight.current.coerceAtLeast(1)
    val window = cache.window(scope.items, state.offset, viewportHeight, stickToEnd)

    composableContainer(
        name = "LazyColumn",
        modifier = modifier,
        measurableFactory = { children ->
            LazyColumnMeasurable(
                modifier = modifier,
                children = children,
                visibleItems = window.items,
                startLine = window.startLine,
                totalEstimatedHeight = window.totalEstimatedHeight,
                state = state,
                cache = cache,
                stickToEnd = stickToEnd,
            )
        },
    ) {
        window.items.forEach { item ->
            key(item.key) {
                Column { item.content() }
            }
        }
    }
}

interface LazyListScope {
    fun item(
        key: Any? = null,
        content: @Composable () -> Unit,
    )

    fun <T> items(
        list: List<T>,
        key: ((T) -> Any)? = null,
        itemContent: @Composable (T) -> Unit,
    )
}

private data class LazyItem(
    val key: Any,
    val content: @Composable () -> Unit,
)

private class LazyListScopeImpl : LazyListScope {
    val items = mutableListOf<LazyItem>()

    override fun item(
        key: Any?,
        content: @Composable () -> Unit,
    ) {
        val resolvedKey = key ?: "item-${items.size}"
        require(items.none { it.key == resolvedKey }) { "Duplicate lazy item key: $resolvedKey" }
        items += LazyItem(resolvedKey, content)
    }

    override fun <T> items(
        list: List<T>,
        key: ((T) -> Any)?,
        itemContent: @Composable (T) -> Unit,
    ) {
        list.forEachIndexed { index, value ->
            item(key?.invoke(value) ?: "item-${items.size}-$index") { itemContent(value) }
        }
    }
}

private data class LazyWindow(
    val items: List<LazyItem>,
    val startLine: Int,
    val totalEstimatedHeight: Int,
)

private class LazyItemHeightCache {
    private val heights = mutableMapOf<Any, Int>()

    fun window(
        items: List<LazyItem>,
        offset: Int,
        viewportHeight: Int,
        stickToEnd: Boolean,
    ): LazyWindow {
        if (items.isEmpty()) return LazyWindow(emptyList(), 0, 0)
        val totalHeight = items.sumOf { heightOf(it.key) }
        val resolvedOffset =
            if (stickToEnd) {
                (totalHeight - viewportHeight).coerceAtLeast(0)
            } else {
                offset.coerceIn(0, (totalHeight - viewportHeight).coerceAtLeast(0))
            }
        val overscan = viewportHeight
        val windowStart = (resolvedOffset - overscan).coerceAtLeast(0)
        val windowEnd = resolvedOffset + viewportHeight + overscan
        var line = 0
        var first = items.size
        var lastExclusive = 0
        var startLine = 0
        items.forEachIndexed { index, item ->
            val height = heightOf(item.key)
            val itemEnd = line + height
            if (first == items.size && itemEnd > windowStart) {
                first = index
                startLine = line
            }
            if (line < windowEnd) lastExclusive = index + 1
            line = itemEnd
        }
        val resolvedFirst = if (first == items.size) items.lastIndex else first
        val resolvedEnd = lastExclusive.coerceAtLeast(resolvedFirst + 1).coerceAtMost(items.size)
        return LazyWindow(
            items = items.subList(resolvedFirst, resolvedEnd),
            startLine = startLine,
            totalEstimatedHeight = totalHeight,
        )
    }

    fun update(
        key: Any,
        height: Int,
    ) {
        heights[key] = height.coerceAtLeast(1)
    }

    private fun heightOf(key: Any): Int = heights[key] ?: 1
}

private class LazyColumnMeasurable(
    override val modifier: Modifier,
    private val children: List<Measurable>,
    private val visibleItems: List<LazyItem>,
    private val startLine: Int,
    private val totalEstimatedHeight: Int,
    private val state: ScrollState,
    private val cache: LazyItemHeightCache,
    private val stickToEnd: Boolean,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 80
        val viewportHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val placeables =
            children.map { child ->
                child.measure(Constraints(maxWidth = width, maxHeight = Int.MAX_VALUE))
            }
        visibleItems.zip(placeables).forEach { (item, placeable) -> cache.update(item.key, placeable.height) }
        val rendered = placeables.flatMap { it.lines }
        state.contentHeight = totalEstimatedHeight
        state.viewportHeight = if (viewportHeight == Int.MAX_VALUE) rendered.size else viewportHeight
        state.offset = if (stickToEnd) state.maxOffset else state.offset.coerceIn(0, state.maxOffset)
        val localOffset = (state.offset - startLine).coerceAtLeast(0)
        val lines =
            if (viewportHeight == Int.MAX_VALUE) {
                rendered.drop(localOffset)
            } else {
                rendered.drop(localOffset).take(viewportHeight).let { visible ->
                    visible + List((viewportHeight - visible.size).coerceAtLeast(0)) { "" }
                }
            }
        return SimplePlaceable(
            width = width,
            height = lines.size,
            lines = lines,
        )
    }
}
