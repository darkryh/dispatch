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
import com.ead.dispatch.modifier.focusable
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A single action row in a [SelectMenu].
 *
 * @param label Text shown for the action.
 * @param enabled Whether the action can be focused and invoked (rows that are disabled are skipped
 *   during navigation and cannot fire [onSelect]).
 * @param onSelect Invoked when this row is confirmed with Enter/Return.
 */
data class MenuItem(
    val label: String,
    val enabled: Boolean = true,
    val onSelect: () -> Unit,
)

/**
 * A generic action row carrying a typed [value], for callers that want their handler to receive the
 * backing data rather than closing over it.
 *
 * @param T The payload type.
 * @param value The data associated with this row.
 * @param label Text shown for the action.
 * @param enabled Whether the action can be focused and invoked.
 * @param onSelect Invoked with [value] when this row is confirmed.
 */
data class MenuEntry<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val onSelect: (T) -> Unit,
) {
    /**
     * Adapt this typed entry to a plain [MenuItem] that invokes [onSelect] with [value].
     */
    fun toMenuItem(): MenuItem = MenuItem(label = label, enabled = enabled, onSelect = { onSelect(value) })
}

/**
 * State holder for a [SelectMenu]: tracks the focused row via [cursorIndex].
 *
 * Hoist this with [rememberSelectMenuState] when you need to read or drive the selection from
 * outside the menu; otherwise the default-remembered instance is enough.
 *
 * @param initialIndex Row the cursor starts on.
 */
class SelectMenuState(
    initialIndex: Int = 0,
) {
    /**
     * Index of the currently focused row (0-based).
     */
    var cursorIndex: Int by mutableStateOf(initialIndex)
        internal set
}

/**
 * Remember a [SelectMenuState].
 *
 * @param initialIndex Row the cursor starts on.
 */
@Composable
fun rememberSelectMenuState(initialIndex: Int = 0): SelectMenuState = remember { SelectMenuState(initialIndex) }

/**
 * A vertical action menu: arrow Up/Down move the highlight and Enter/Return invokes the focused
 * row's `onSelect`. Disabled rows are skipped during navigation and never fire.
 *
 * Keyboard is active only while the menu is focused, mirroring [Button]'s focus model
 * (`focusable` modifier + [LocalFocusRegistry] + [LocalKeyboardInterceptor]). Tab/Shift+Tab move
 * focus to the next/previous focusable.
 *
 * When [visibleCount] is set (or the menu is taller than the terminal viewport) rows are windowed
 * around the cursor using the same slice math as [SelectableWindowedList].
 *
 * Example:
 * ```kotlin
 * SelectMenu(
 *     items = listOf(
 *         MenuItem("New file") { createFile() },
 *         MenuItem("Open...") { open() },
 *         MenuItem("Quit", enabled = canQuit) { quit() },
 *     ),
 * )
 * ```
 *
 * @param items Menu rows, in display order.
 * @param modifier Modifiers to apply (the menu is made `focusable` on top of this).
 * @param state Hoisted cursor state.
 * @param cursorIndicator Prefix shown on the focused row (blank-padded on other rows).
 * @param styles Prefix/cursor styling, shared with [SelectableList].
 * @param visibleCount Maximum visible rows; `null` falls back to the terminal viewport height.
 * @param itemStyle Style for an enabled, unfocused row label.
 * @param focusedStyle Style for the focused row label.
 * @param disabledStyle Style for a disabled row label.
 */
@Composable
fun SelectMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    state: SelectMenuState = rememberSelectMenuState(),
    cursorIndicator: String = "> ",
    styles: SelectableListStyles = SelectableListStyles(prefix = TextStyle(), selectedPrefix = TextStyle()),
    visibleCount: Int? = null,
    itemStyle: TextStyle? = null,
    focusedStyle: TextStyle? = null,
    disabledStyle: TextStyle? = null,
) {
    val focusRegistry = LocalFocusRegistry.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val terminalHeight = LocalTerminalHeight.current
    val focusToken = remember { Any() }

    // Keep the cursor on a valid, enabled row before rendering.
    if (items.isNotEmpty()) {
        if (state.cursorIndex > items.lastIndex) state.cursorIndex = items.lastIndex
        if (state.cursorIndex < 0) state.cursorIndex = 0
        if (!items[state.cursorIndex].enabled) {
            state.cursorIndex = firstEnabledIndex(items, state.cursorIndex)
        }
    }

    // The interceptor closure outlives a single composition; read the latest list through a ref.
    val itemsRef = remember { SelectMenuMutableRef(items) }.also { it.value = items }

    DisposableEffect(focusRegistry, keyboardInterceptor) {
        val dispose =
            keyboardInterceptor.register(priority = -1) { rawEvent ->
                if (!focusRegistry.isFocused(focusToken) || !focusRegistry.claimEvent(rawEvent)) {
                    return@register false
                }
                val list = itemsRef.value
                if (list.isEmpty()) return@register false
                val event = rawEvent.asKeyEvent()
                when {
                    event.key == Key.Tab && !event.ctrl && !event.alt -> {
                        if (event.shift) focusRegistry.focusPrevious() else focusRegistry.focusNext()
                        true
                    }
                    event.key == Key.ArrowUp -> {
                        state.cursorIndex = nextEnabledIndex(list, state.cursorIndex, -1)
                        true
                    }
                    event.key == Key.ArrowDown -> {
                        state.cursorIndex = nextEnabledIndex(list, state.cursorIndex, +1)
                        true
                    }
                    event.key == Key.Enter -> {
                        list.getOrNull(state.cursorIndex)?.takeIf { it.enabled }?.onSelect?.invoke()
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
            val prefixStyle = if (focused) styles.selectedPrefix else styles.prefix
            val labelStyle =
                when {
                    !item.enabled -> disabledStyle
                    focused -> focusedStyle
                    else -> itemStyle
                }

            Row {
                Text(if (focused) cursorIndicator else cursorPad, style = prefixStyle)
                Text(item.label, style = labelStyle)
            }
        }
    }
}

/**
 * First enabled index at or after [from], wrapping to the start; falls back to [from] when no row is
 * enabled.
 */
private fun firstEnabledIndex(items: List<MenuItem>, from: Int): Int {
    if (items.isEmpty()) return 0
    for (offset in items.indices) {
        val index = (from + offset) % items.size
        if (items[index].enabled) return index
    }
    return from
}

/**
 * Next enabled index from [from] stepping by [direction] (+1 down, -1 up), wrapping around; returns
 * [from] when no other row is enabled.
 */
private fun nextEnabledIndex(items: List<MenuItem>, from: Int, direction: Int): Int {
    if (items.isEmpty()) return from
    var index = from
    repeat(items.size) {
        index = (index + direction + items.size) % items.size
        if (items[index].enabled) return index
    }
    return from
}

/**
 * Minimal mutable reference so the long-lived keyboard interceptor closure can read the latest
 * composition values. Copied (kept private) from the same pattern used by the sample's `SelectMenu`.
 */
private class SelectMenuMutableRef<T>(
    var value: T,
)
