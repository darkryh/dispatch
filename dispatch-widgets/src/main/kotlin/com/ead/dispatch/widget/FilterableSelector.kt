package com.ead.dispatch.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.ajalt.mordant.input.KeyboardEvent

internal class FilterableSelectorState<T>(
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

internal data class SelectorKeyBindings(
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onConfirm: (() -> Boolean)? = null,
    val onCancel: (() -> Boolean)? = null,
    val onTab: (() -> Boolean)? = null,
    val onBackspace: (() -> Boolean)? = null,
    val onCharacter: ((Char) -> Boolean)? = null,
    val isCharacterEvent: (KeyboardEvent) -> Boolean = { event ->
        event.key.length == 1 && !event.ctrl && !event.alt
    },
)

internal fun handleSelectorKeyEvent(
    event: KeyboardEvent,
    bindings: SelectorKeyBindings,
): Boolean =
    when (event.key) {
        "ArrowUp" -> {
            bindings.onMoveUp()
            true
        }
        "ArrowDown" -> {
            bindings.onMoveDown()
            true
        }
        "Enter" -> bindings.onConfirm?.invoke() ?: false
        "Escape" -> bindings.onCancel?.invoke() ?: false
        "Tab" -> bindings.onTab?.invoke() ?: false
        "Backspace" -> bindings.onBackspace?.invoke() ?: false
        else -> {
            val onCharacter = bindings.onCharacter
            if (onCharacter != null && bindings.isCharacterEvent(event)) {
                onCharacter(event.key[0])
            } else {
                false
            }
        }
    }
