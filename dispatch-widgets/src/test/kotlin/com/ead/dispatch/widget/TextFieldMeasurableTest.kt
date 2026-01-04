package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFieldMeasurableTest {
    private fun terminal(width: Int = 20, height: Int = 10): Terminal =
        Terminal(ansiLevel = AnsiLevel.TRUECOLOR, width = width, height = height, interactive = false)

    @Test
    fun `wraps into multiple lines when unconstrained`() {
        val measurable = TextFieldMeasurable(
            value = "abc def ghi jkl",
            icon = "",
            placeholder = "",
            enabled = true,
            singleLine = false,
            modifier = Modifier,
            showCursor = false,
            cursorChar = "|",
            maxLines = null,
            terminal = terminal(10)
        )

        val placeable = measurable.measure(Constraints(maxWidth = 10, maxHeight = 5))
        assertTrue(placeable.height >= 2, "should wrap into multiple lines")
        assertEquals(10, placeable.width)
    }

    @Test
    fun `respects maxLines`() {
        val measurable = TextFieldMeasurable(
            value = "a b c d e f g h i j k l m n o p",
            icon = "",
            placeholder = "",
            enabled = true,
            singleLine = false,
            modifier = Modifier,
            showCursor = false,
            cursorChar = "|",
            maxLines = 2,
            terminal = terminal(8)
        )

        val placeable = measurable.measure(Constraints(maxWidth = 8, maxHeight = 5))
        assertEquals(2, placeable.height, "maxLines should cap height")
    }

    @Test
    fun `pads lines to measured height`() {
        val measurable = TextFieldMeasurable(
            value = "short",
            icon = "",
            placeholder = "",
            enabled = true,
            singleLine = false,
            modifier = Modifier,
            showCursor = false,
            cursorChar = "|",
            maxLines = 3,
            terminal = terminal(10)
        )

        val placeable = measurable.measure(Constraints(maxWidth = 10, maxHeight = 3))
        assertEquals(placeable.height, placeable.lines.size)
        assertEquals(1, placeable.height)
    }

    @Test
    fun `cursor does not affect placeholder wrapping`() {
        val placeholder = "1234567890"
        val withCursor = TextFieldMeasurable(
            value = "",
            icon = "",
            placeholder = placeholder,
            enabled = true,
            singleLine = false,
            modifier = Modifier,
            showCursor = true,
            cursorChar = "|",
            maxLines = null,
            terminal = terminal(10)
        )
        val withoutCursor = TextFieldMeasurable(
            value = "",
            icon = "",
            placeholder = placeholder,
            enabled = true,
            singleLine = false,
            modifier = Modifier,
            showCursor = false,
            cursorChar = "|",
            maxLines = null,
            terminal = terminal(10)
        )

        val withCursorPlaceable = withCursor.measure(Constraints(maxWidth = 10, maxHeight = 5))
        val withoutCursorPlaceable = withoutCursor.measure(Constraints(maxWidth = 10, maxHeight = 5))

        assertEquals(withoutCursorPlaceable.height, withCursorPlaceable.height)
    }
}
