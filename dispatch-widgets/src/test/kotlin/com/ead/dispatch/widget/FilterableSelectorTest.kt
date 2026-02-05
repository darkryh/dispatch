package com.ead.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterableSelectorTest {

    @Test
    fun `selection clamps when options shrink`() {
        val state = FilterableSelectorState<String>(
            initialVisible = true,
            initialSelectedIndex = 3,
        )
        state.updateFilteredOptions(listOf("A", "B"))

        assertEquals(1, state.selectedIndex)
        state.updateFilteredOptions(emptyList())
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun `handleSelectorKeyEvent routes arrow keys and characters`() {
        var movedUp = false
        var movedDown = false
        var captured: Char? = null

        val bindings = SelectorKeyBindings(
            onMoveUp = { movedUp = true },
            onMoveDown = { movedDown = true },
            onCharacter = {
                captured = it
                true
            },
        )

        assertTrue(handleSelectorKeyEvent(KeyboardEvent("ArrowUp"), bindings))
        assertTrue(handleSelectorKeyEvent(KeyboardEvent("ArrowDown"), bindings))
        assertTrue(handleSelectorKeyEvent(KeyboardEvent("a"), bindings))

        assertTrue(movedUp)
        assertTrue(movedDown)
        assertEquals('a', captured)
    }
}
