package io.github.darkryh.dispatch.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the mapping from Mordant's canonical W3C key strings onto [Key]. These canonical names are
 * identical across Linux/macOS/Windows (Mordant normalizes raw bytes per-OS before we see them), so
 * this table doubles as documentation of exactly what Dispatch expects from the terminal.
 */
class KeyMappingTest {
    @Test
    fun `named keys map to their Named value`() {
        assertEquals(Key.ArrowUp, keyEvent("ArrowUp").key)
        assertEquals(Key.ArrowDown, keyEvent("ArrowDown").key)
        assertEquals(Key.ArrowLeft, keyEvent("ArrowLeft").key)
        assertEquals(Key.ArrowRight, keyEvent("ArrowRight").key)
        assertEquals(Key.Enter, keyEvent("Enter").key)
        assertEquals(Key.Escape, keyEvent("Escape").key)
        assertEquals(Key.Tab, keyEvent("Tab").key)
        assertEquals(Key.Backspace, keyEvent("Backspace").key)
        assertEquals(Key.Delete, keyEvent("Delete").key)
        assertEquals(Key.Insert, keyEvent("Insert").key)
        assertEquals(Key.Home, keyEvent("Home").key)
        assertEquals(Key.End, keyEvent("End").key)
        assertEquals(Key.PageUp, keyEvent("PageUp").key)
        assertEquals(Key.PageDown, keyEvent("PageDown").key)
        assertEquals(Key.PasteStart, keyEvent("PasteStart").key)
        assertEquals(Key.PasteEnd, keyEvent("PasteEnd").key)
    }

    @Test
    fun `space maps to the printable space char and is text`() {
        val event = keyEvent(" ")
        assertEquals(Key.Space, event.key)
        assertEquals(Key.Char(' '), event.key)
        assertEquals(' ', event.char)
        assertTrue(event.isText)
    }

    @Test
    fun `printable characters map to Char`() {
        assertEquals(Key.Char('a'), keyEvent("a").key)
        assertEquals(Key.Char('Q'), keyEvent("Q").key)
        assertEquals(Key.Char('1'), keyEvent("1").key)
        assertEquals(Key.Char('€'), keyEvent("€").key)
        assertEquals('a', keyEvent("a").char)
    }

    @Test
    fun `function keys map to Named F-values`() {
        assertEquals(Key.Named.F1, keyEvent("F1").key)
        assertEquals(Key.Named.F5, keyEvent("F5").key)
        assertEquals(Key.Named.F12, keyEvent("F12").key)
    }

    @Test
    fun `unidentified and multi-char payloads map to Unknown`() {
        assertEquals(Key.Named.Unknown, keyEvent("Unidentified").key)
        assertEquals(Key.Named.Unknown, keyEvent("pasted multi-char text").key)
        assertEquals(Key.Named.Unknown, keyEvent("F99").key)
    }

    @Test
    fun `legacy alternate spellings Mordant never emits do not accidentally match`() {
        // These were dead defensive branches in the old code; assert they are NOT named keys.
        assertEquals(Key.Named.Unknown, keyEvent("Esc").key)
        assertEquals(Key.Named.Unknown, keyEvent("Return").key)
        // "Up"/"Down" are 2-char strings -> Unknown, not arrows.
        assertEquals(Key.Named.Unknown, keyEvent("Up").key)
        assertEquals(Key.Named.Unknown, keyEvent("Down").key)
        // The named "Space" string Mordant never sends -> 5-char -> Unknown (real space is " ").
        assertEquals(Key.Named.Unknown, keyEvent("Space").key)
    }

    @Test
    fun `modifiers are exposed`() {
        val event = keyEvent("p", ctrl = true)
        assertTrue(event.ctrl)
        assertFalse(event.alt)
        assertEquals('p', event.char)
    }

    @Test
    fun `ctrl char stroke matches case-insensitively and ignores shift`() {
        val stroke = ctrl('p')
        assertTrue(stroke.matches(keyEvent("p", ctrl = true)))
        assertTrue(stroke.matches(keyEvent("P", ctrl = true)))
        assertTrue(stroke.matches(keyEvent("p", ctrl = true, shift = true)))
        assertFalse(stroke.matches(keyEvent("p")))
        assertFalse(stroke.matches(keyEvent("p", ctrl = true, alt = true)))
    }

    @Test
    fun `shift char stroke requires shift`() {
        val stroke = shift('q')
        assertTrue(stroke.matches(keyEvent("q", shift = true)))
        assertTrue(stroke.matches(keyEvent("Q", shift = true)))
        assertFalse(stroke.matches(keyEvent("q")))
    }

    @Test
    fun `named stroke matches modifiers exactly`() {
        val stroke = Key.Enter.ctrl
        assertTrue(stroke.matches(keyEvent("Enter", ctrl = true)))
        assertFalse(stroke.matches(keyEvent("Enter")))
        assertFalse(stroke.matches(keyEvent("Enter", ctrl = true, shift = true)))
    }

    @Test
    fun `plain named stroke rejects modifiers`() {
        val stroke = Key.Tab.stroke()
        assertTrue(stroke.matches(keyEvent("Tab")))
        assertFalse(stroke.matches(keyEvent("Tab", shift = true)))
    }

    @Test
    fun `isChar honors ignoreCase flag`() {
        assertTrue(keyEvent("Q").isChar('q'))
        assertFalse(keyEvent("Q").isChar('q', ignoreCase = false))
        assertNull(keyEvent("Enter").char)
    }

    @Test
    fun `stroke label formats modifiers and key`() {
        assertEquals("Ctrl+p", ctrl('p').label())
        assertEquals("Shift+Tab", Key.Tab.shift.label())
        assertEquals("Enter", Key.Enter.stroke().label())
    }
}
