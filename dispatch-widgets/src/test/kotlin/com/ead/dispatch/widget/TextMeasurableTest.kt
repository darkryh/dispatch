package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextMeasurableTest {
    private fun terminal(
        width: Int = 20,
        height: Int = 10,
    ): Terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, width = width, height = height, interactive = false)

    @Test
    fun `styled text measures visible width`() {
        val measurable =
            TextMeasurable(
                text = "hi",
                modifier = Modifier,
                style = TextColors.brightWhite,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = false,
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 10, maxHeight = 10))
        assertEquals(2, placeable.width)
        assertEquals(1, placeable.height)
        assertTrue(placeable.lines.single().contains("hi"))
    }

    @Test
    fun `styled prompt prefix measures correctly`() {
        val measurable =
            TextMeasurable(
                text = "> ",
                modifier = Modifier,
                style = TextColors.brightWhite,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = false,
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 10, maxHeight = 10))
        assertEquals(2, placeable.width)
    }

    @Test
    fun `text wraps when bounded width`() {
        val measurable =
            TextMeasurable(
                text = "hello world",
                modifier = Modifier,
                style = null,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = false,
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 6, maxHeight = 10))
        assertEquals(listOf("hello", "world"), placeable.lines)
    }

    @Test
    fun `markdown renders emphasis`() {
        val measurable =
            TextMeasurable(
                text = "**bold** and *italic*",
                modifier = Modifier,
                style = TextColors.white,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = true,
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 30, maxHeight = 10))
        val rendered = placeable.lines.joinToString("\n")

        assertTrue(rendered.contains("bold"))
        assertTrue(rendered.contains("italic"))
        assertTrue(!rendered.contains("**bold**"))
        assertTrue(!rendered.contains("*italic*"))
    }

    @Test
    fun `markdown flag controls parsing behavior`() {
        val source = "**bold**"
        val constraints = Constraints(maxWidth = 30, maxHeight = 10)

        val plain =
            TextMeasurable(
                text = source,
                modifier = Modifier,
                style = null,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = false,
                terminal = terminal(),
            ).measure(constraints).lines.joinToString("\n")

        val markdown =
            TextMeasurable(
                text = source,
                modifier = Modifier,
                style = null,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = true,
                terminal = terminal(),
            ).measure(constraints).lines.joinToString("\n")

        assertTrue(plain.contains("**bold**"))
        assertTrue(markdown.contains("bold"))
        assertTrue(!markdown.contains("**bold**"))
    }
}
