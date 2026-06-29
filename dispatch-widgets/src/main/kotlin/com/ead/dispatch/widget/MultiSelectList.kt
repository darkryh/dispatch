package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.focusable
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.rememberCallback
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * State holder for a [MultiSelectList].
 *
 * Unlike the read-only [Checklist], this tracks both the set of currently selected
 * keys ([selectedKeys]) and a movable [cursorIndex]. The cursor is what the keyboard
 * moves with Up/Down; Space toggles whichever row the cursor is on.
 *
 * Hoist this with [rememberMultiSelectState] so selection survives recomposition.
 *
 * @param K The stable key type used to identify selected items.
 * @param initialSelected Keys that should start out checked.
 */
class MultiSelectState<K>(
    initialSelected: Set<K> = emptySet(),
) {
    /**
     * Keys of the currently checked items.
     */
    var selectedKeys: Set<K> by mutableStateOf(initialSelected)
        internal set

    /**
     * Index of the row the cursor is currently on (0-based).
     */
    var cursorIndex: Int by mutableStateOf(0)
        internal set

    /**
     * Number of rows currently rendered. Updated by [MultiSelectList] during composition so
     * [moveDown] can clamp the cursor without the state needing to know the item list.
     */
    internal var itemCount: Int = 0

    /**
     * Toggle the checked state of [key].
     */
    fun toggle(key: K) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    /**
     * Move the cursor up by one row (clamped to the first row).
     */
    fun moveUp() {
        if (itemCount == 0) return
        cursorIndex = (cursorIndex - 1).coerceAtLeast(0)
    }

    /**
     * Move the cursor down by one row (clamped to the last row).
     */
    fun moveDown() {
        if (itemCount == 0) return
        cursorIndex = (cursorIndex + 1).coerceAtMost(itemCount - 1)
    }

    /**
     * Add every key in [keys] to the selection (union; existing selections are kept).
     */
    fun selectAll(keys: Collection<K>) {
        selectedKeys = selectedKeys + keys
    }

    /**
     * Clear the entire selection.
     */
    fun clear() {
        selectedKeys = emptySet()
    }
}

/**
 * Remember a [MultiSelectState].
 *
 * @param initialSelected Keys that should start out checked.
 */
@Composable
fun <K> rememberMultiSelectState(initialSelected: Set<K> = emptySet()): MultiSelectState<K> =
    remember { MultiSelectState(initialSelected) }

/**
 * An interactive, keyboard-driven multi-select list.
 *
 * This is the real multi-select counterpart to the read-only [Checklist]: a movable cursor
 * navigates rows (Up/Down) and Space toggles the row under the cursor. Selection is held in a
 * hoisted [MultiSelectState] keyed by [key].
 *
 * Keyboard (active only while focused, mirroring [Button]'s focus model):
 * - Up / Down: move the cursor
 * - Space: toggle the cursor row
 * - Enter / Return: confirm (invokes [onConfirm])
 * - Tab / Shift+Tab: move focus to the next/previous focusable
 *
 * When [visibleCount] is set (or the list is taller than the terminal viewport) the rows are
 * windowed around the cursor using the same slice math as [SelectableWindowedList].
 *
 * Example:
 * ```kotlin
 * data class Task(val id: String, val name: String)
 * val tasks = listOf(Task("a", "Review"), Task("b", "Design"))
 * val state = rememberMultiSelectState<String>()
 *
 * MultiSelectList(
 *     items = tasks,
 *     key = { it.id },
 *     state = state,
 *     onSelectionChange = { selected -> viewModel.updateSelection(selected) },
 * ) { task, checked, focused ->
 *     Text(task.name)
 * }
 * ```
 *
 * @param T The item type.
 * @param K The stable key type identifying each item.
 * @param items Items to display.
 * @param key Extracts the stable selection key for an item.
 * @param modifier Modifiers to apply (the list is made `focusable` on top of this).
 * @param state Hoisted selection + cursor state.
 * @param checkedGlyph Glyph shown for checked rows.
 * @param uncheckedGlyph Glyph shown for unchecked rows.
 * @param cursorIndicator Prefix shown on the cursor row (blank-padded on other rows).
 * @param visibleCount Maximum visible rows; `null` falls back to the terminal viewport height.
 * @param styles Prefix/cursor styling, shared with [SelectableList].
 * @param onSelectionChange Invoked with the new selection whenever a row is toggled.
 * @param onConfirm Invoked with the current selection when Enter/Return is pressed.
 * @param itemContent Renders a row's body given the item, its checked state, and whether it is focused.
 */
@Composable
fun <T, K> MultiSelectList(
    items: List<T>,
    key: (T) -> K,
    modifier: Modifier = Modifier,
    state: MultiSelectState<K> = rememberMultiSelectState(),
    checkedGlyph: String = "[x] ",
    uncheckedGlyph: String = "[ ] ",
    cursorIndicator: String = "> ",
    visibleCount: Int? = null,
    styles: SelectableListStyles = SelectableListStyles(prefix = TextStyle(), selectedPrefix = TextStyle()),
    onSelectionChange: (Set<K>) -> Unit = {},
    onConfirm: (Set<K>) -> Unit = {},
    itemContent: @Composable (item: T, checked: Boolean, focused: Boolean) -> Unit,
) {
    val focusRegistry = LocalFocusRegistry.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val terminalHeight = LocalTerminalHeight.current
    val focusToken = remember { Any() }

    val onSelectionChangeCallback = rememberCallback(onSelectionChange)
    val onConfirmCallback = rememberCallback(onConfirm)

    // Keep the cursor and item count in sync with the latest item list before rendering.
    state.itemCount = items.size
    if (state.cursorIndex > items.lastIndex) {
        state.cursorIndex = items.lastIndex.coerceAtLeast(0)
    }

    // The interceptor closure outlives a single composition; read the latest list/key through refs.
    val itemsRef = remember { MultiSelectMutableRef(items) }.also { it.value = items }
    val keyRef = remember { MultiSelectMutableRef(key) }.also { it.value = key }

    DisposableEffect(focusRegistry, keyboardInterceptor) {
        val dispose =
            keyboardInterceptor.register(priority = -1) { event ->
                if (!focusRegistry.isFocused(focusToken) || !focusRegistry.claimEvent(event)) {
                    return@register false
                }
                when {
                    event.key == "Tab" && !event.ctrl && !event.alt -> {
                        if (event.shift) focusRegistry.focusPrevious() else focusRegistry.focusNext()
                        true
                    }
                    event.key == "ArrowUp" || event.key == "Up" -> {
                        state.moveUp()
                        true
                    }
                    event.key == "ArrowDown" || event.key == "Down" -> {
                        state.moveDown()
                        true
                    }
                    event.key == "Space" || event.key == " " -> {
                        val list = itemsRef.value
                        val item = list.getOrNull(state.cursorIndex)
                        if (item != null) {
                            state.toggle(keyRef.value(item))
                            onSelectionChangeCallback(state.selectedKeys)
                        }
                        true
                    }
                    event.key == "Enter" || event.key == "Return" -> {
                        onConfirmCallback(state.selectedKeys)
                        true
                    }
                    else -> false
                }
            }
        onDispose { dispose() }
    }

    if (items.isEmpty()) return

    // Window around the cursor using the same slice math as SelectableWindowedList.
    val effectiveVisible = (visibleCount ?: terminalHeight).coerceAtLeast(1)
    val useWindow = items.size > effectiveVisible
    val slice = if (useWindow) computeWindowSlice(items, state.cursorIndex, effectiveVisible) else null
    val windowItems = slice?.window ?: items
    val startIndex = slice?.startIndex ?: 0

    val cursorPad = " ".repeat(cursorIndicator.length)

    Column(modifier = modifier.focusable(focusToken)) {
        windowItems.forEachIndexed { localIndex, item ->
            val absoluteIndex = startIndex + localIndex
            val focused = absoluteIndex == state.cursorIndex
            val checked = state.selectedKeys.contains(key(item))
            val prefixStyle = if (focused) styles.selectedPrefix else styles.prefix

            Row {
                Text(if (focused) cursorIndicator else cursorPad, style = prefixStyle)
                Text(if (checked) checkedGlyph else uncheckedGlyph, style = prefixStyle)
                itemContent(item, checked, focused)
            }
        }
    }
}

/**
 * Minimal mutable reference so the long-lived keyboard interceptor closure can read the latest
 * composition values. Copied (kept private) from the same pattern used by the sample's `SelectMenu`.
 */
private class MultiSelectMutableRef<T>(
    var value: T,
)
