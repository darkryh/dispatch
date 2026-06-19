package com.ead.dispatch.widget

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DividerMeasurableTest {
    @Test
    fun `HorizontalDivider with bounded width fills width`() {
        val measurable =
            HorizontalDividerMeasurable(
                modifier = Modifier,
                char = '─',
            )

        val placeable = measurable.measure(Constraints(maxWidth = 20))
        assertEquals(20, placeable.width, "should fill available width")
        assertEquals(1, placeable.height, "should be single line")
        assertEquals("─".repeat(20), placeable.lines.first(), "should be filled with divider char")
    }

    @Test
    fun `HorizontalDivider with unbounded width has minimal width`() {
        val measurable =
            HorizontalDividerMeasurable(
                modifier = Modifier,
                char = '─',
            )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.width, "unbounded should have width 1")
        assertEquals(1, placeable.height, "should be single line")
    }

    @Test
    fun `HorizontalDivider uses custom character`() {
        val measurable =
            HorizontalDividerMeasurable(
                modifier = Modifier,
                char = '*',
            )

        val placeable = measurable.measure(Constraints(maxWidth = 5))
        assertEquals("*****", placeable.lines.first(), "should use custom character")
    }

    @Test
    fun `VerticalDivider with bounded height fills height`() {
        val measurable =
            VerticalDividerMeasurable(
                modifier = Modifier,
                char = '│',
            )

        val placeable = measurable.measure(Constraints(maxHeight = 5))
        assertEquals(1, placeable.width, "should be single character wide")
        assertEquals(5, placeable.height, "should fill available height")
        assertEquals(5, placeable.lines.size, "should have 5 lines")
        assertTrue(placeable.lines.all { it == "│" }, "all lines should be divider char")
    }

    @Test
    fun `VerticalDivider with unbounded height has minimal height`() {
        val measurable =
            VerticalDividerMeasurable(
                modifier = Modifier,
                char = '│',
            )

        val placeable = measurable.measure(Constraints())
        assertEquals(1, placeable.width, "should be single character wide")
        assertEquals(1, placeable.height, "unbounded should have height 1")
    }

    @Test
    fun `VerticalDivider uses custom character`() {
        val measurable =
            VerticalDividerMeasurable(
                modifier = Modifier,
                char = '|',
            )

        val placeable = measurable.measure(Constraints(maxHeight = 3))
        assertTrue(placeable.lines.all { it == "|" }, "all lines should use custom character")
    }

    @Test
    fun `DividerStyle Light uses correct characters`() {
        assertEquals('─', DividerStyle.Light.horizontal, "Light horizontal should be ─")
        assertEquals('│', DividerStyle.Light.vertical, "Light vertical should be │")
    }

    @Test
    fun `DividerStyle Heavy uses correct characters`() {
        assertEquals('━', DividerStyle.Heavy.horizontal, "Heavy horizontal should be ━")
        assertEquals('┃', DividerStyle.Heavy.vertical, "Heavy vertical should be ┃")
    }

    @Test
    fun `DividerStyle Double uses correct characters`() {
        assertEquals('═', DividerStyle.Double.horizontal, "Double horizontal should be ═")
        assertEquals('║', DividerStyle.Double.vertical, "Double vertical should be ║")
    }

    @Test
    fun `DividerStyle Dashed uses correct characters`() {
        assertEquals('┄', DividerStyle.Dashed.horizontal, "Dashed horizontal should be ┄")
        assertEquals('┆', DividerStyle.Dashed.vertical, "Dashed vertical should be ┆")
    }

    @Test
    fun `DividerStyle Dotted uses correct characters`() {
        assertEquals('·', DividerStyle.Dotted.horizontal, "Dotted horizontal should be ·")
        assertEquals('·', DividerStyle.Dotted.vertical, "Dotted vertical should be ·")
    }

    @Test
    fun `DividerStyle Space uses space characters`() {
        assertEquals(' ', DividerStyle.Space.horizontal, "Space horizontal should be space")
        assertEquals(' ', DividerStyle.Space.vertical, "Space vertical should be space")
    }

    @Test
    fun `HorizontalDivider with zero width constraint`() {
        val measurable =
            HorizontalDividerMeasurable(
                modifier = Modifier,
                char = '─',
            )

        val placeable = measurable.measure(Constraints(maxWidth = 0))
        assertEquals(0, placeable.width, "should have zero width")
        assertEquals(1, placeable.height, "should still have height 1")
    }

    @Test
    fun `VerticalDivider with zero height constraint`() {
        val measurable =
            VerticalDividerMeasurable(
                modifier = Modifier,
                char = '│',
            )

        val placeable = measurable.measure(Constraints(maxHeight = 0))
        assertEquals(1, placeable.width, "should still have width 1")
        assertEquals(0, placeable.height, "should have zero height")
    }

    @Test
    fun `HorizontalDivider always has height 1`() {
        val measurable =
            HorizontalDividerMeasurable(
                modifier = Modifier,
                char = '─',
            )

        val placeable = measurable.measure(Constraints(maxWidth = 100, maxHeight = 50))
        assertEquals(1, placeable.height, "horizontal divider should always be height 1")
    }

    @Test
    fun `VerticalDivider always has width 1`() {
        val measurable =
            VerticalDividerMeasurable(
                modifier = Modifier,
                char = '│',
            )

        val placeable = measurable.measure(Constraints(maxWidth = 100, maxHeight = 50))
        assertEquals(1, placeable.width, "vertical divider should always be width 1")
    }
}
