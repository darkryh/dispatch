package com.ead.dispatch.runtime

import com.ead.dispatch.input.Key
import com.ead.dispatch.input.ctrl
import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyBindingsTypedTest {
    @Test
    fun `typed on(Key) overload builds a binding that matches the right named key`() {
        var fired = 0
        val scope =
            KeyBindingsScope().apply {
                on(Key.Enter, description = "send") { fired++ }
            }

        assertTrue(scope.handle(KeyboardEvent("Enter")))
        assertEquals(1, fired)

        assertFalse(scope.handle(KeyboardEvent("Escape")))
        assertEquals(1, fired)
    }

    @Test
    fun `typed on(Key) ignores modifiers for a plain stroke`() {
        // A plain Key stroke requires no modifiers and must not match when Ctrl is held.
        var fired = 0
        val scope =
            KeyBindingsScope().apply {
                on(Key.Enter) { fired++ }
            }

        assertFalse(scope.handle(KeyboardEvent("Enter", ctrl = true)))
        assertEquals(0, fired)

        assertTrue(scope.handle(KeyboardEvent("Enter")))
        assertEquals(1, fired)
    }

    @Test
    fun `typed on(KeyStroke) overload matches a Ctrl chord and nothing else`() {
        var fired = 0
        val scope =
            KeyBindingsScope().apply {
                on(ctrl('p'), description = "go to…") { fired++ }
            }

        // Ctrl+P matches, case-insensitively, with Ctrl required.
        assertTrue(scope.handle(KeyboardEvent("p", ctrl = true)))
        assertTrue(scope.handle(KeyboardEvent("P", ctrl = true)))
        assertEquals(2, fired)

        // Plain 'p' (no Ctrl) must not fire.
        assertFalse(scope.handle(KeyboardEvent("p")))
        // A different char with Ctrl must not fire.
        assertFalse(scope.handle(KeyboardEvent("q", ctrl = true)))
        assertEquals(2, fired)
    }

    @Test
    fun `typed binding exposes its description and stroke`() {
        val scope =
            KeyBindingsScope().apply {
                on(Key.Enter, description = "send") {}
                on(ctrl('p')) {}
            }

        val bindings = scope.bindings()
        assertEquals(2, bindings.size)
        assertEquals("send", bindings[0].description)
        assertEquals(Key.Enter, bindings[0].stroke.key)
    }
}
