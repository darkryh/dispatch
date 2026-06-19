package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.ajalt.mordant.input.KeyboardEvent

private val FILTER_TEXT_PARSE_CONFIG =
    KeyEventTextParseConfig(
        mapSpaceKeyToSpace = true,
        replaceNewlineWithSpace = true,
        replaceTabWithSpace = true,
        extraNonTextKeys = setOf("Up", "Down"),
    )

class FilterInputController(
    private val state: TextFieldState,
) {
    fun handleKeyEvent(event: KeyboardEvent): Boolean {
        when (event.key) {
            "ArrowUp", "ArrowDown", "Enter", "Escape", "Esc", "Tab" -> return false
        }

        if (event.alt) return false

        if (event.ctrl) {
            return handleCtrlShortcut(event)
        }

        if (event.shift) {
            when (event.key) {
                "ArrowLeft" -> {
                    updateSelection(delta = -1)
                    return true
                }
                "ArrowRight" -> {
                    updateSelection(delta = 1)
                    return true
                }
                "Home" -> {
                    updateSelectionTo(0)
                    return true
                }
                "End" -> {
                    updateSelectionTo(state.value.length)
                    return true
                }
            }
        }

        return when (event.key) {
            "Backspace" -> {
                state.deleteBackward()
                true
            }
            "Delete" -> {
                state.deleteForward()
                true
            }
            "ArrowLeft" -> {
                state.cursorPosition = (state.cursorPosition - 1).coerceAtLeast(0)
                state.clearSelection()
                true
            }
            "ArrowRight" -> {
                state.cursorPosition = (state.cursorPosition + 1).coerceAtMost(state.value.length)
                state.clearSelection()
                true
            }
            "Home" -> {
                state.cursorPosition = 0
                state.clearSelection()
                true
            }
            "End" -> {
                state.cursorPosition = state.value.length
                state.clearSelection()
                true
            }
            else -> {
                val text = parseTextFromKeyEvent(event, FILTER_TEXT_PARSE_CONFIG)
                if (!text.isNullOrEmpty()) {
                    if (state.hasSelection()) {
                        state.deleteSelection()
                    }
                    state.insert(text)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleCtrlShortcut(event: KeyboardEvent): Boolean =
        when (event.key.lowercase()) {
            "a" -> {
                state.selectionStart = 0
                state.selectionEnd = state.value.length
                state.cursorPosition = state.value.length
                true
            }
            "backspace" -> {
                deleteWordBeforeCursor()
                true
            }
            "delete" -> {
                deleteWordAfterCursor()
                true
            }
            else -> false
        }

    private fun updateSelection(delta: Int) {
        val anchor = state.selectionStart ?: state.cursorPosition
        val newPos = (state.cursorPosition + delta).coerceIn(0, state.value.length)
        state.cursorPosition = newPos
        state.selectionStart = anchor
        state.selectionEnd = newPos
        if (!state.hasSelection()) {
            state.clearSelection()
        }
    }

    private fun updateSelectionTo(target: Int) {
        val anchor = state.selectionStart ?: state.cursorPosition
        val newPos = target.coerceIn(0, state.value.length)
        state.cursorPosition = newPos
        state.selectionStart = anchor
        state.selectionEnd = newPos
        if (!state.hasSelection()) {
            state.clearSelection()
        }
    }

    private fun deleteWordBeforeCursor() {
        if (state.hasSelection()) {
            state.deleteSelection()
            return
        }
        val value = state.value
        var pos = state.cursorPosition.coerceIn(0, value.length)
        if (pos == 0) return
        while (pos > 0 && value[pos - 1].isWhitespace()) pos--
        while (pos > 0 && !value[pos - 1].isWhitespace()) pos--
        val before = value.substring(0, pos)
        val after = value.substring(state.cursorPosition)
        state.value = before + after
        state.cursorPosition = pos
        state.clearSelection()
    }

    private fun deleteWordAfterCursor() {
        if (state.hasSelection()) {
            state.deleteSelection()
            return
        }
        val value = state.value
        var pos = state.cursorPosition.coerceIn(0, value.length)
        if (pos >= value.length) return
        while (pos < value.length && value[pos].isWhitespace()) pos++
        while (pos < value.length && !value[pos].isWhitespace()) pos++
        val before = value.substring(0, state.cursorPosition)
        val after = value.substring(pos)
        state.value = before + after
        state.clearSelection()
    }
}

@Composable
fun rememberFilterInputController(state: TextFieldState): FilterInputController = remember(state) { FilterInputController(state) }
