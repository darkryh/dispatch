package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundMeasurableTest {
    private fun terminal(
        width: Int = 20,
        height: Int = 10,
    ): Terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, width = width, height = height, interactive = false)

    private fun normalizeFillStyle(fillStyle: TextStyle): TextStyle =
        when {
            fillStyle.bgColor != null -> fillStyle
            fillStyle.color != null -> fillStyle.bg
            else -> fillStyle
        }

    private fun splitStyleWrapper(style: TextStyle): Pair<String, String> {
        val sentinel = "§§DISPATCH_BG_SENTINEL§§"
        val styled = style.invoke(sentinel)
        val index = styled.indexOf(sentinel)
        if (index < 0) return "" to ""
        return styled.substring(0, index) to styled.substring(index + sentinel.length)
    }

    private class StubMeasurable(
        override val modifier: Modifier = Modifier,
        private val placeable: Placeable,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable = placeable
    }

    @Test
    fun `adds top and bottom rules`() {
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 3,
                        height = 1,
                        lines = listOf("> █"),
                    ),
            )

        val measurable =
            BackgroundMeasurable(
                modifier = Modifier,
                style = BackgroundStyle.lines(char = '─', style = null),
                children = listOf(child),
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 10))
        assertEquals(3, placeable.height)
        assertEquals("─".repeat(10), placeable.lines[0])
        assertEquals("> █", placeable.lines[1])
        assertEquals("─".repeat(10), placeable.lines[2])
    }

    @Test
    fun `fill pads content to width`() {
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 3,
                        height = 1,
                        lines = listOf("> █"),
                    ),
            )

        val measurable =
            BackgroundMeasurable(
                modifier = Modifier,
                style =
                    BackgroundStyle.fill(
                        fill = TextStyle(),
                        paddingHorizontal = 0,
                        paddingVertical = 0,
                    ),
                children = listOf(child),
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 6))
        assertEquals(1, placeable.height)
        assertEquals("> █" + " ".repeat(3), placeable.lines.single())
    }

    @Test
    fun `fill treats foreground color as background`() {
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 1,
                        height = 1,
                        lines = listOf("X"),
                    ),
            )

        val measurable =
            BackgroundMeasurable(
                modifier = Modifier,
                style =
                    BackgroundStyle.fill(
                        fill = TextColors.red,
                        paddingHorizontal = 0,
                        paddingVertical = 0,
                    ),
                children = listOf(child),
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 2))
        val line = placeable.lines.single()
        assertTrue(line.contains("\u001B[41m"), "expected a red background ANSI code in the fill output")
    }

    @Test
    fun `fill reapplies after inner background resets`() {
        val childLine = TextColors.brightCyan.bg("X") + "Hello"
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 6,
                        height = 1,
                        lines = listOf(childLine),
                    ),
            )

        val measurable =
            BackgroundMeasurable(
                modifier = Modifier,
                style =
                    BackgroundStyle.fill(
                        fill = TextColors.red,
                        paddingHorizontal = 2,
                        paddingVertical = 0,
                    ),
                children = listOf(child),
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 20))
        val line = placeable.lines.single()

        val (fillPrefix, _) = splitStyleWrapper(normalizeFillStyle(TextColors.red))
        assertTrue(
            line.contains("\u001B[49m$fillPrefix"),
            "expected fill to be re-applied after inner background resets so padding stays filled",
        )
    }

    @Test
    fun `fill reapplies after combined fg+bg reset`() {
        val childLine = "\u001B[31;47mX\u001B[39;49mHello"
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 6,
                        height = 1,
                        lines = listOf(childLine),
                    ),
            )

        val measurable =
            BackgroundMeasurable(
                modifier = Modifier,
                style =
                    BackgroundStyle.fill(
                        fill = TextColors.red,
                        paddingHorizontal = 2,
                        paddingVertical = 0,
                    ),
                children = listOf(child),
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 20))
        val line = placeable.lines.single()

        val (fillPrefix, _) = splitStyleWrapper(normalizeFillStyle(TextColors.red))
        assertTrue(
            line.contains("\u001B[39;49m$fillPrefix"),
            "expected fill to be re-applied after combined SGR resets so padding stays filled",
        )
    }

    @Test
    fun `fill applies to padding`() {
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 1,
                        height = 1,
                        lines = listOf("X"),
                    ),
            )

        val measurable =
            BackgroundMeasurable(
                modifier = Modifier,
                style =
                    BackgroundStyle.fill(
                        fill = TextColors.red,
                        paddingHorizontal = 2,
                        paddingVertical = 0,
                    ),
                children = listOf(child),
                terminal = terminal(),
            )

        val placeable = measurable.measure(Constraints(maxWidth = 8))
        val line = placeable.lines.single()
        assertTrue(line.contains("\u001B[41m"), "expected background fill to be applied")
    }
}
