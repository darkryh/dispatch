package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Reusable state engine for filterable, keyboard-navigable selectors.
 *
 * Holds visibility, the current selection index, the filter text and the currently filtered
 * options, and exposes the navigation/filter mutations shared by selector widgets (such as
 * a session selector). Callers feed the already-filtered options via [updateFilteredOptions] and
 * drive the selection through [moveUp]/[moveDown] and the filter through
 * [appendFilter]/[removeLastFilter]/[clearFilter].
 *
 * @param T The type of the options being selected.
 * @param initialVisible Initial visibility of the selector.
 * @param initialSelectedIndex Initial selected index within the filtered options.
 * @param initialFilterText Initial filter text.
 */
class FilterableSelectorState<T>(
    initialVisible: Boolean,
    initialSelectedIndex: Int,
    initialFilterText: String = "",
) {
    var isVisible: Boolean by mutableStateOf(initialVisible)
    var selectedIndex: Int by mutableStateOf(initialSelectedIndex)
    var filterText: String by mutableStateOf(initialFilterText)
    var filteredOptions: List<T> by mutableStateOf(emptyList())

    val selectedOption: T?
        get() = filteredOptions.getOrNull(selectedIndex)

    val hasResults: Boolean
        get() = filteredOptions.isNotEmpty()

    fun updateFilteredOptions(options: List<T>) {
        filteredOptions = options
        ensureSelectionInBounds()
    }

    fun ensureSelectionInBounds() {
        if (selectedIndex >= filteredOptions.size) {
            selectedIndex = (filteredOptions.size - 1).coerceAtLeast(0)
        }
    }

    fun resetSelection() {
        selectedIndex = 0
    }

    fun moveUp() {
        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
    }

    fun moveDown() {
        if (filteredOptions.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1).coerceAtMost(filteredOptions.size - 1)
        }
    }

    fun appendFilter(char: Char) {
        filterText += char
        resetSelection()
    }

    fun removeLastFilter() {
        if (filterText.isNotEmpty()) {
            filterText = filterText.dropLast(1)
            resetSelection()
        }
    }

    fun clearFilter() {
        filterText = ""
        resetSelection()
    }
}

/**
 * Remember a [FilterableSelectorState] across recompositions.
 *
 * @param T The type of the options being selected.
 * @param initialVisible Initial visibility of the selector.
 * @param initialSelectedIndex Initial selected index within the filtered options.
 * @param initialFilterText Initial filter text.
 * @return A composition-scoped [FilterableSelectorState].
 */
@Composable
fun <T> rememberSelectorState(
    initialVisible: Boolean = true,
    initialSelectedIndex: Int = 0,
    initialFilterText: String = "",
): FilterableSelectorState<T> =
    remember { FilterableSelectorState(initialVisible, initialSelectedIndex, initialFilterText) }

/**
 * Declarative key bindings for a filterable selector, consumed by [handleSelectorKeyEvent].
 *
 * Each callback returns `true` when it consumes the event. Navigation callbacks
 * ([onMoveUp]/[onMoveDown]) always consume. Optional callbacks default to "not handled".
 *
 * @param onMoveUp Invoked on ArrowUp.
 * @param onMoveDown Invoked on ArrowDown.
 * @param onConfirm Invoked on Enter; returns whether the event was consumed.
 * @param onCancel Invoked on Escape; returns whether the event was consumed.
 * @param onTab Invoked on Tab; returns whether the event was consumed.
 * @param onBackspace Invoked on Backspace; returns whether the event was consumed.
 * @param onCharacter Invoked for a printable character event; returns whether it was consumed.
 * @param isCharacterEvent Predicate identifying a printable single-character event.
 */
data class SelectorKeyBindings(
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onConfirm: (() -> Boolean)? = null,
    val onCancel: (() -> Boolean)? = null,
    val onTab: (() -> Boolean)? = null,
    val onBackspace: (() -> Boolean)? = null,
    val onCharacter: ((Char) -> Boolean)? = null,
    val isCharacterEvent: (KeyboardEvent) -> Boolean = { event ->
        event.asKeyEvent().isText
    },
)

/**
 * Dispatch a [KeyboardEvent] to the matching callback in [bindings].
 *
 * Maps ArrowUp/ArrowDown/Enter/Escape/Tab/Backspace to their bindings, and routes printable
 * single-character events to [SelectorKeyBindings.onCharacter].
 *
 * @param event The incoming keyboard event.
 * @param bindings The selector's key bindings.
 * @return `true` if the event was consumed, `false` otherwise.
 */
fun handleSelectorKeyEvent(
    event: KeyboardEvent,
    bindings: SelectorKeyBindings,
): Boolean {
    val keyEvent = event.asKeyEvent()
    return when (keyEvent.key) {
        Key.ArrowUp -> {
            bindings.onMoveUp()
            true
        }
        Key.ArrowDown -> {
            bindings.onMoveDown()
            true
        }
        Key.Enter -> bindings.onConfirm?.invoke() ?: false
        Key.Escape -> bindings.onCancel?.invoke() ?: false
        Key.Tab -> bindings.onTab?.invoke() ?: false
        Key.Backspace -> bindings.onBackspace?.invoke() ?: false
        else -> {
            val onCharacter = bindings.onCharacter
            val char = keyEvent.char
            if (onCharacter != null && char != null && bindings.isCharacterEvent(event)) {
                onCharacter(char)
            } else {
                false
            }
        }
    }
}
