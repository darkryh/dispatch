package com.ead.dispatch.runtime

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExitKeyBindingTest {
    @Test
    fun `matches respects modifiers`() {
        val binding = ExitKeyBinding(key = "Q", ctrl = true, alt = false, shift = false)

        assertTrue(binding.matches(KeyboardEvent("Q", ctrl = true)))
        assertFalse(binding.matches(KeyboardEvent("Q")))
        assertFalse(binding.matches(KeyboardEvent("Q", ctrl = true, shift = true)))
    }

    @Test
    fun `label formats modifiers`() {
        val binding = ExitKeyBinding(key = "Esc", ctrl = true, alt = true, shift = true)
        assertTrue(binding.label().contains("Ctrl"))
        assertTrue(binding.label().contains("Alt"))
        assertTrue(binding.label().contains("Shift"))
        assertTrue(binding.label().contains("Esc"))
    }
}
