package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.HibernationRegistry
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
    // The scope must be rebuilt each composition (the `content` closure captures fresh
    // values), but this only collects keys + content providers — composition of an item's
    // body is deferred until it is selected into the visible window below.
    val scope = LazyListScopeImpl()
    scope.content()
    val entries = scope.entries
    val cache = remember { LazyItemHeightCache() }
    DisposableEffect(cache) {
        // Drop this LazyColumn's measured heights when the app hibernates; unregister on dispose
        // so a removed list leaves no dangling releaser.
        val handle = HibernationRegistry.registerReleaser { cache.clear() }
        onDispose { handle.close() }
    }
    val viewportHeight = LocalTerminalHeight.current.coerceAtLeast(1)
    val window = cache.window(entries, state.offset, viewportHeight, stickToEnd)

    composableContainer(
        name = "LazyColumn",
        modifier = modifier,
        measurableFactory = { children ->
            LazyColumnMeasurable(
                modifier = modifier,
                children = children,
                visibleKeys = window.keys,
                startLine = window.startLine,
                totalEstimatedHeight = window.totalEstimatedHeight,
                state = state,
                cache = cache,
                stickToEnd = stickToEnd,
            )
        },
    ) {
        // Only the windowed entries have their @Composable content materialized.
        for (index in window.startIndex until window.endExclusive) {
            val entry = entries[index]
            key(entry.key) {
                Column { entry.content() }
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

private class LazyEntry(
    val key: Any,
    val content: @Composable () -> Unit,
)

private class LazyListScopeImpl : LazyListScope {
    val entries = mutableListOf<LazyEntry>()
    private val seenKeys = HashSet<Any>()

    override fun item(
        key: Any?,
        content: @Composable () -> Unit,
    ) {
        val resolvedKey = key ?: "item-${entries.size}"
        // O(1) membership check instead of an O(n) scan per item (which made adding all
        // items O(n^2) on every recomposition).
        require(seenKeys.add(resolvedKey)) { "Duplicate lazy item key: $resolvedKey" }
        entries += LazyEntry(resolvedKey, content)
    }

    override fun <T> items(
        list: List<T>,
        key: ((T) -> Any)?,
        itemContent: @Composable (T) -> Unit,
    ) {
        list.forEachIndexed { index, value ->
            item(key?.invoke(value) ?: "item-${entries.size}-$index") { itemContent(value) }
        }
    }
}

private class LazyWindow(
    val keys: List<Any>,
    val startIndex: Int,
    val endExclusive: Int,
    val startLine: Int,
    val totalEstimatedHeight: Int,
)

/** Test-only hooks for inspecting [LazyColumn] internals. */
internal object LazyColumnTestHooks {
    @Volatile
    var lastHeightCacheSize: Int = 0
        private set

    fun recordHeightCacheSize(size: Int) {
        lastHeightCacheSize = size
    }
}

private class LazyItemHeightCache {
    private val heights = mutableMapOf<Any, Int>()

    fun window(
        entries: List<LazyEntry>,
        offset: Int,
        viewportHeight: Int,
        stickToEnd: Boolean,
    ): LazyWindow {
        // Prune stale heights for keys no longer present (otherwise the map only grows).
        if (heights.size > entries.size) {
            val currentKeys = HashSet<Any>(entries.size)
            entries.forEach { currentKeys.add(it.key) }
            heights.keys.retainAll(currentKeys)
        }
        LazyColumnTestHooks.recordHeightCacheSize(heights.size)
        if (entries.isEmpty()) return LazyWindow(emptyList(), 0, 0, 0, 0)
        val totalHeight = entries.sumOf { heightOf(it.key) }
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
        var first = entries.size
        var lastExclusive = 0
        var startLine = 0
        entries.forEachIndexed { index, entry ->
            val height = heightOf(entry.key)
            val itemEnd = line + height
            if (first == entries.size && itemEnd > windowStart) {
                first = index
                startLine = line
            }
            if (line < windowEnd) lastExclusive = index + 1
            line = itemEnd
        }
        val resolvedFirst = if (first == entries.size) entries.lastIndex else first
        val resolvedEnd = lastExclusive.coerceAtLeast(resolvedFirst + 1).coerceAtMost(entries.size)
        return LazyWindow(
            keys = entries.subList(resolvedFirst, resolvedEnd).map { it.key },
            startIndex = resolvedFirst,
            endExclusive = resolvedEnd,
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

    fun clear() {
        heights.clear()
    }
}

private class LazyColumnMeasurable(
    override val modifier: Modifier,
    private val children: List<Measurable>,
    private val visibleKeys: List<Any>,
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
        visibleKeys.zip(placeables).forEach { (key, placeable) -> cache.update(key, placeable.height) }
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
