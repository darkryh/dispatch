package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.Composer

/**
 * A lazy, scrollable column. Content is provided via a scope that supports [item] and [items].
 *
 * Example:
 * ```
 * LazyColumn(state = rememberScrollState()) {
 *     item { Header() }
 *     items(messages) { message ->
 *         Text(message)
 *     }
 * }
 * ```
 */
@Dispatchable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    content: @Dispatchable LazyListScope.() -> Unit,
) {
    val composer = Composer.current
    val node = composer.startNode("LazyColumn")

    // Build the item lambdas from the scope
    val scope = LazyListScopeImpl().apply(content)
    val itemLambdas = scope.items

    for (block in itemLambdas) {
        block()
    }
    val itemMeasurables = node.children

    val listMeasurable = ScrollableListMeasurable(
        modifier = modifier,
        itemMeasurables = itemMeasurables,
        scrollState = state,
    )

    composer.registerMeasurable(listMeasurable)
    composer.endNode()
}

/**
 * Scope for constructing lazy list content.
 */
interface LazyListScope {
    fun item(content: @Dispatchable () -> Unit)
    fun <T> items(list: List<T>, itemContent: @Dispatchable (T) -> Unit)
}

internal class LazyListScopeImpl : LazyListScope {
    internal val items = mutableListOf<@Dispatchable () -> Unit>()

    override fun item(content: @Dispatchable () -> Unit) {
        items += content
    }

    override fun <T> items(list: List<T>, itemContent: @Dispatchable (T) -> Unit) {
        list.forEach { value -> items += { itemContent(value) } }
    }
}
