package com.ead.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyEventTextParserTest {
    @Test
    fun `default parser preserves multiline text`() {
        assertEquals("hello\nworld", parseTextFromKeyEvent(KeyboardEvent("hello\r\nworld")))
        assertEquals("a\tb", parseTextFromKeyEvent(KeyboardEvent("a\tb")))
    }

    @Test
    fun `single-line config normalizes spaces and newlines`() {
        val config =
            KeyEventTextParseConfig(
                mapSpaceKeyToSpace = true,
                replaceNewlineWithSpace = true,
                replaceTabWithSpace = true,
            )

        assertEquals(" ", parseTextFromKeyEvent(KeyboardEvent("Space"), config))
        assertEquals("a b", parseTextFromKeyEvent(KeyboardEvent("a\nb"), config))
        assertEquals("a b", parseTextFromKeyEvent(KeyboardEvent("a\tb"), config))
    }

    @Test
    fun `parser rejects non-text and modified events`() {
        assertNull(parseTextFromKeyEvent(KeyboardEvent("ArrowDown")))
        assertNull(parseTextFromKeyEvent(KeyboardEvent("F5")))
        assertNull(parseTextFromKeyEvent(KeyboardEvent("x", ctrl = true)))
        assertNull(parseTextFromKeyEvent(KeyboardEvent("x", alt = true)))
    }

    @Test
    fun `parser supports extra non-text keys`() {
        val config = KeyEventTextParseConfig(extraNonTextKeys = setOf("Up", "Down"))
        assertNull(parseTextFromKeyEvent(KeyboardEvent("Up"), config))
        assertNull(parseTextFromKeyEvent(KeyboardEvent("Down"), config))
    }
}
