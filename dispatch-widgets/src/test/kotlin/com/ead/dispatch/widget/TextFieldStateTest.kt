package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextFieldStateTest {

    @Test
    fun `initial value is set correctly`() {
        val state = TextFieldState("Hello")
        assertEquals("Hello", state.value, "initial value should be set")
        assertEquals(5, state.cursorPosition, "cursor should be at end of initial value")
    }

    @Test
    fun `empty initial value works`() {
        val state = TextFieldState()
        assertEquals("", state.value, "default value should be empty")
        assertEquals(0, state.cursorPosition, "cursor should be at 0")
    }

    @Test
    fun `insert adds text at cursor position`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 5
        state.insert(" World")
        assertEquals("Hello World", state.value, "should insert at cursor")
        assertEquals(11, state.cursorPosition, "cursor should move after inserted text")
    }

    @Test
    fun `insert at beginning`() {
        val state = TextFieldState("World")
        state.cursorPosition = 0
        state.insert("Hello ")
        assertEquals("Hello World", state.value, "should insert at beginning")
        assertEquals(6, state.cursorPosition, "cursor should be after inserted text")
    }

    @Test
    fun `insert in middle`() {
        val state = TextFieldState("Hllo")
        state.cursorPosition = 1
        state.insert("e")
        assertEquals("Hello", state.value, "should insert in middle")
        assertEquals(2, state.cursorPosition, "cursor should be after inserted character")
    }

    @Test
    fun `deleteBackward removes character before cursor`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 5
        state.deleteBackward()
        assertEquals("Hell", state.value, "should delete last character")
        assertEquals(4, state.cursorPosition, "cursor should move back")
    }

    @Test
    fun `deleteBackward at beginning does nothing`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 0
        state.deleteBackward()
        assertEquals("Hello", state.value, "should not delete anything")
        assertEquals(0, state.cursorPosition, "cursor should stay at 0")
    }

    @Test
    fun `deleteBackward in middle`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 3
        state.deleteBackward()
        assertEquals("Helo", state.value, "should delete character before cursor")
        assertEquals(2, state.cursorPosition, "cursor should move back")
    }

    @Test
    fun `deleteForward removes character at cursor`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 0
        state.deleteForward()
        assertEquals("ello", state.value, "should delete first character")
        assertEquals(0, state.cursorPosition, "cursor should stay at same position")
    }

    @Test
    fun `deleteForward at end does nothing`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 5
        state.deleteForward()
        assertEquals("Hello", state.value, "should not delete anything")
        assertEquals(5, state.cursorPosition, "cursor should stay at end")
    }

    @Test
    fun `deleteForward in middle`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 2
        state.deleteForward()
        assertEquals("Helo", state.value, "should delete character at cursor")
        assertEquals(2, state.cursorPosition, "cursor should stay at same position")
    }

    @Test
    fun `moveCursorLeft decreases cursor position`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 3
        state.moveCursorLeft()
        assertEquals(2, state.cursorPosition, "cursor should move left")
    }

    @Test
    fun `moveCursorLeft at beginning stays at 0`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 0
        state.moveCursorLeft()
        assertEquals(0, state.cursorPosition, "cursor should stay at 0")
    }

    @Test
    fun `moveCursorRight increases cursor position`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 2
        state.moveCursorRight()
        assertEquals(3, state.cursorPosition, "cursor should move right")
    }

    @Test
    fun `moveCursorRight at end stays at end`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 5
        state.moveCursorRight()
        assertEquals(5, state.cursorPosition, "cursor should stay at end")
    }

    @Test
    fun `moveCursorToStart moves cursor to 0`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 3
        state.moveCursorToStart()
        assertEquals(0, state.cursorPosition, "cursor should be at start")
    }

    @Test
    fun `moveCursorToEnd moves cursor to value length`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 0
        state.moveCursorToEnd()
        assertEquals(5, state.cursorPosition, "cursor should be at end")
    }

    @Test
    fun `hasSelection returns false when no selection`() {
        val state = TextFieldState("Hello")
        assertFalse(state.hasSelection(), "should return false when no selection")
    }

    @Test
    fun `hasSelection returns false when start equals end`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 2
        state.selectionEnd = 2
        assertFalse(state.hasSelection(), "should return false when start equals end")
    }

    @Test
    fun `hasSelection returns true when selection exists`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        assertTrue(state.hasSelection(), "should return true when selection exists")
    }

    @Test
    fun `clearSelection clears selection values`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        state.clearSelection()
        assertNull(state.selectionStart, "selectionStart should be null")
        assertNull(state.selectionEnd, "selectionEnd should be null")
    }

    @Test
    fun `deleteSelection removes selected text`() {
        val state = TextFieldState("Hello World")
        state.selectionStart = 5
        state.selectionEnd = 11
        state.deleteSelection()
        assertEquals("Hello", state.value, "should delete selected text")
    }

    @Test
    fun `deleteSelection handles reversed selection`() {
        val state = TextFieldState("Hello World")
        state.selectionStart = 11
        state.selectionEnd = 5
        state.deleteSelection()
        assertEquals("Hello", state.value, "should handle reversed selection")
    }

    @Test
    fun `deleteSelection does nothing without selection`() {
        val state = TextFieldState("Hello")
        state.deleteSelection()
        assertEquals("Hello", state.value, "should not change value without selection")
    }

    @Test
    fun `deleteBackward with selection deletes selection`() {
        val state = TextFieldState("Hello World")
        state.selectionStart = 0
        state.selectionEnd = 6
        state.deleteBackward()
        assertEquals("World", state.value, "should delete selection on backspace")
    }

    @Test
    fun `deleteForward with selection deletes selection`() {
        val state = TextFieldState("Hello World")
        state.selectionStart = 5
        state.selectionEnd = 11
        state.deleteForward()
        assertEquals("Hello", state.value, "should delete selection on delete")
    }

    @Test
    fun `insert clears selection`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        state.insert("X")
        assertNull(state.selectionStart, "selectionStart should be cleared")
        assertNull(state.selectionEnd, "selectionEnd should be cleared")
    }

    @Test
    fun `moveCursorLeft clears selection`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        state.moveCursorLeft()
        assertNull(state.selectionStart, "selectionStart should be cleared")
        assertNull(state.selectionEnd, "selectionEnd should be cleared")
    }

    @Test
    fun `moveCursorRight clears selection`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        state.moveCursorRight()
        assertNull(state.selectionStart, "selectionStart should be cleared")
        assertNull(state.selectionEnd, "selectionEnd should be cleared")
    }

    @Test
    fun `moveCursorToStart clears selection`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        state.moveCursorToStart()
        assertNull(state.selectionStart, "selectionStart should be cleared")
        assertNull(state.selectionEnd, "selectionEnd should be cleared")
    }

    @Test
    fun `moveCursorToEnd clears selection`() {
        val state = TextFieldState("Hello")
        state.selectionStart = 1
        state.selectionEnd = 4
        state.moveCursorToEnd()
        assertNull(state.selectionStart, "selectionStart should be cleared")
        assertNull(state.selectionEnd, "selectionEnd should be cleared")
    }

    @Test
    fun `hasFocus is false by default`() {
        val state = TextFieldState("Hello")
        assertFalse(state.hasFocus, "hasFocus should be false by default")
    }

    @Test
    fun `hasFocus can be set`() {
        val state = TextFieldState("Hello")
        state.hasFocus = true
        assertTrue(state.hasFocus, "hasFocus should be settable")
    }

    @Test
    fun `cursor position is clamped on insert`() {
        val state = TextFieldState("Hello")
        state.cursorPosition = 100 // Out of bounds
        state.insert("X")
        assertTrue(state.value.contains("X"), "insert should work with out of bounds cursor")
    }
}
