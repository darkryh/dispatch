package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * A node in a [Tree].
 *
 * @param T The value type carried by every node.
 * @param value The value rendered for this node.
 * @param children The child nodes; an empty list marks a leaf.
 */
data class TreeNode<T>(
    val value: T,
    val children: List<TreeNode<T>> = emptyList(),
)

/**
 * State holder for [Tree].
 *
 * Tracks the set of expanded node keys and the index of the selected row within the currently
 * visible (flattened) node list. Expansion is keyed (not positional) so the expanded/collapsed
 * state survives reordering or insertion of nodes.
 *
 * @param initialExpandedKeys Keys of nodes that start expanded.
 * @param initialSelectedIndex The initially selected row index.
 */
class TreeState(
    initialExpandedKeys: Set<Any> = emptySet(),
    initialSelectedIndex: Int = 0,
) {
    /**
     * Keys of the nodes that are currently expanded.
     */
    var expandedKeys: Set<Any> by mutableStateOf(initialExpandedKeys)
        internal set

    /**
     * Index of the selected row within the visible (flattened) node list.
     */
    var selectedIndex: Int by mutableStateOf(initialSelectedIndex)
        internal set

    /**
     * Whether the node identified by [key] is expanded.
     */
    fun isExpanded(key: Any): Boolean = key in expandedKeys

    /**
     * Mark the node identified by [key] as expanded.
     */
    fun expand(key: Any) {
        if (key !in expandedKeys) expandedKeys = expandedKeys + key
    }

    /**
     * Mark the node identified by [key] as collapsed.
     */
    fun collapse(key: Any) {
        if (key in expandedKeys) expandedKeys = expandedKeys - key
    }

    /**
     * Toggle the expanded state of the node identified by [key].
     */
    fun toggle(key: Any) {
        if (key in expandedKeys) collapse(key) else expand(key)
    }

    /**
     * Move the selection up by one.
     */
    fun moveUp() {
        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
    }

    /**
     * Move the selection down by one, bounded by [lastIndex].
     */
    fun moveDown(lastIndex: Int) {
        selectedIndex = (selectedIndex + 1).coerceAtMost(lastIndex.coerceAtLeast(0))
    }

    internal fun clampSelection(lastIndex: Int) {
        val bounded = selectedIndex.coerceIn(0, lastIndex.coerceAtLeast(0))
        if (bounded != selectedIndex) selectedIndex = bounded
    }
}

/**
 * Remember a [TreeState].
 *
 * @param initialExpandedKeys Keys of nodes that start expanded.
 * @param initialSelectedIndex The initially selected row index.
 */
@Composable
fun rememberTreeState(
    initialExpandedKeys: Set<Any> = emptySet(),
    initialSelectedIndex: Int = 0,
): TreeState = remember { TreeState(initialExpandedKeys, initialSelectedIndex) }

/**
 * An expandable/collapsible tree view with keyboard navigation.
 *
 * The visible (expanded) nodes are flattened into a single list and each is rendered on its own row,
 * indented by [indentPerLevel] cells per depth level and prefixed with an expand/collapse glyph
 * ([expandedGlyph]/[collapsedGlyph] for branches, [leafGlyph] for leaves). Keyboard handling is
 * registered through [LocalKeyboardInterceptor]:
 * - Arrow Up/Down: move the selection
 * - Arrow Right or Enter: expand the selected branch (Enter toggles)
 * - Arrow Left or Enter: collapse the selected branch (Enter toggles)
 *
 * When [visibleCount] is set, rows are windowed with the same slice math as the session selector. The
 * tree performs no direct terminal writes — it is a pure measure/compose widget, so it never
 * flickers.
 *
 * Example:
 * ```kotlin
 * Tree(
 *     roots = listOf(
 *         TreeNode("src", listOf(TreeNode("main.kt"), TreeNode("util.kt"))),
 *         TreeNode("README.md"),
 *     ),
 *     nodeKey = { it },
 * ) { value, _, _ ->
 *     Text(value)
 * }
 * ```
 *
 * @param T The value type carried by every node.
 * @param roots The top-level nodes.
 * @param modifier Modifiers to apply to the tree column.
 * @param nodeKey Projects a node value into a stable key used for expansion tracking.
 * @param indentPerLevel Number of cells of indentation added per depth level.
 * @param expandedGlyph Glyph shown before an expanded branch.
 * @param collapsedGlyph Glyph shown before a collapsed branch.
 * @param leafGlyph Glyph shown before a leaf node.
 * @param selectionIndicator Text prefix shown for the selected row (empty disables the prefix).
 * @param visibleCount Maximum number of rows visible in the scroll window (null = render all).
 * @param enabled Whether the tree responds to keyboard input.
 * @param state State holder for expansion and selection.
 * @param nodeContent Renders the content of a node given its value, depth and expanded state.
 */
@Composable
fun <T> Tree(
    roots: List<TreeNode<T>>,
    modifier: Modifier = Modifier,
    nodeKey: (T) -> Any = { it as Any },
    indentPerLevel: Int = 2,
    expandedGlyph: String = "▾ ",
    collapsedGlyph: String = "▸ ",
    leafGlyph: String = "  ",
    selectionIndicator: String = "> ",
    visibleCount: Int? = null,
    enabled: Boolean = true,
    state: TreeState = rememberTreeState(),
    nodeContent: @Composable (T, depth: Int, expanded: Boolean) -> Unit,
) {
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    // Flatten the currently-expanded nodes into a single, ordered row list.
    val rows = flattenTree(roots, nodeKey) { state.isExpanded(it) }

    // Keep the selection within the visible rows when the tree shrinks (e.g. after a collapse).
    state.clampSelection(rows.lastIndex)

    // The key handler is registered once but must always read the latest flattened rows; a state
    // cell updated every composition gives it a stable handle to fresh data.
    val rowsState = remember { mutableStateOf<List<TreeRow<T>>>(emptyList()) }
    rowsState.value = rows

    fun handleKeyEvent(rawEvent: KeyboardEvent): Boolean {
        val currentRows = rowsState.value
        val current = currentRows.getOrNull(state.selectedIndex)
        val event = rawEvent.asKeyEvent()
        return when (event.key) {
            Key.ArrowUp -> {
                state.moveUp()
                true
            }
            Key.ArrowDown -> {
                state.moveDown(currentRows.lastIndex)
                true
            }
            Key.ArrowRight -> {
                if (current != null && current.hasChildren) state.expand(current.key)
                true
            }
            Key.ArrowLeft -> {
                if (current != null && current.hasChildren) state.collapse(current.key)
                true
            }
            Key.Enter -> {
                if (current != null && current.hasChildren) state.toggle(current.key)
                true
            }
            else -> false
        }
    }

    DisposableEffect(listOf(state, enabled, keyboardInterceptor)) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }
        val interceptorDispose =
            keyboardInterceptor.register { event ->
                handleKeyEvent(event)
            }
        onDispose {
            interceptorDispose()
        }
    }

    // Window the rows when a visible count is requested.
    val windowSlice =
        if (visibleCount != null) {
            computeWindowSlice(rows, state.selectedIndex, visibleCount)
        } else {
            WindowSlice(rows, state.selectedIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)), 0, rows.size)
        }
    val visibleRows = windowSlice.window

    Column(modifier = modifier) {
        visibleRows.forEachIndexed { localIndex, treeRow ->
            val isSelected = localIndex == windowSlice.localSelected
            val glyph =
                when {
                    !treeRow.hasChildren -> leafGlyph
                    treeRow.expanded -> expandedGlyph
                    else -> collapsedGlyph
                }
            val prefix =
                selectionIndicator.takeIf { it.isNotEmpty() }?.let { indicator ->
                    if (isSelected) indicator else " ".repeat(indicator.length)
                }

            Row {
                if (!prefix.isNullOrEmpty()) {
                    Text(text = prefix)
                }
                val indent = (indentPerLevel * treeRow.depth).coerceAtLeast(0)
                if (indent > 0) {
                    Text(text = " ".repeat(indent))
                }
                Text(text = glyph)
                nodeContent(treeRow.value, treeRow.depth, treeRow.expanded)
            }
        }
    }
}

/**
 * A single flattened, renderable tree row.
 *
 * @param value The node's value.
 * @param depth The node's depth (0 for roots).
 * @param key The node's stable key.
 * @param hasChildren Whether the node has any children.
 * @param expanded Whether the node is currently expanded (always false for leaves).
 */
internal data class TreeRow<T>(
    val value: T,
    val depth: Int,
    val key: Any,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

/**
 * Depth-first flatten of [roots] into the rows that are currently visible, descending into a node's
 * children only when it is expanded.
 */
internal fun <T> flattenTree(
    roots: List<TreeNode<T>>,
    nodeKey: (T) -> Any,
    isExpanded: (Any) -> Boolean,
): List<TreeRow<T>> {
    val result = mutableListOf<TreeRow<T>>()

    fun visit(node: TreeNode<T>, depth: Int) {
        val key = nodeKey(node.value)
        val hasChildren = node.children.isNotEmpty()
        val expanded = hasChildren && isExpanded(key)
        result.add(TreeRow(node.value, depth, key, hasChildren, expanded))
        if (expanded) {
            node.children.forEach { child -> visit(child, depth + 1) }
        }
    }

    roots.forEach { root -> visit(root, 0) }
    return result
}
