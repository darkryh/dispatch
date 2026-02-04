package com.ead.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterInputControllerTest {

    @Test
    fun `inserts characters and advances cursor`() {
        val state = TextFieldState()
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("a")))
        assertEquals("a", state.value)
        assertEquals(1, state.cursorPosition)
    }

    @Test
    fun `space key inserts a space`() {
        val state = TextFieldState()
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("Space")))
        assertEquals(" ", state.value)
        assertEquals(1, state.cursorPosition)
    }

    @Test
    fun `paste inserts multi character chunk`() {
        val state = TextFieldState()
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("hello world")))
        assertEquals("hello world", state.value)
        assertEquals(11, state.cursorPosition)
    }

    @Test
    fun `backspace deletes before cursor`() {
        val state = TextFieldState("ab")
        state.cursorPosition = 2
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("Backspace")))
        assertEquals("a", state.value)
        assertEquals(1, state.cursorPosition)
    }

    @Test
    fun `delete removes character at cursor`() {
        val state = TextFieldState("ab")
        state.cursorPosition = 0
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("Delete")))
        assertEquals("b", state.value)
        assertEquals(0, state.cursorPosition)
    }

    @Test
    fun `left and right arrows move cursor`() {
        val state = TextFieldState("ab")
        state.cursorPosition = 2
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("ArrowLeft")))
        assertEquals(1, state.cursorPosition)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("ArrowRight")))
        assertEquals(2, state.cursorPosition)
    }

    @Test
    fun `home and end move to bounds`() {
        val state = TextFieldState("abc")
        state.cursorPosition = 1
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("Home")))
        assertEquals(0, state.cursorPosition)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("End")))
        assertEquals(3, state.cursorPosition)
    }

    @Test
    fun `shift arrows extend selection`() {
        val state = TextFieldState("abc")
        state.cursorPosition = 3
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("ArrowLeft", shift = true)))
        assertEquals(2, state.cursorPosition)
        assertTrue(state.hasSelection())
    }

    @Test
    fun `ctrl and alt combinations are ignored unless handled`() {
        val state = TextFieldState("abc")
        state.cursorPosition = 3
        val controller = FilterInputController(state)

        assertFalse(controller.handleKeyEvent(KeyboardEvent("x", ctrl = true)))
        assertFalse(controller.handleKeyEvent(KeyboardEvent("x", alt = true)))
        assertEquals("abc", state.value)
        assertEquals(3, state.cursorPosition)
    }

    @Test
    fun `ctrl+a selects all and typing replaces selection`() {
        val state = TextFieldState("alpha")
        state.cursorPosition = 5
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("a", ctrl = true)))
        assertTrue(state.hasSelection())
        assertTrue(controller.handleKeyEvent(KeyboardEvent("b")))
        assertEquals("b", state.value)
        assertEquals(1, state.cursorPosition)
    }

    @Test
    fun `ctrl+backspace deletes previous word`() {
        val state = TextFieldState("hello world")
        state.cursorPosition = 11
        val controller = FilterInputController(state)

        assertTrue(controller.handleKeyEvent(KeyboardEvent("Backspace", ctrl = true)))
        assertEquals("hello ", state.value)
        assertEquals(6, state.cursorPosition)
    }
}
