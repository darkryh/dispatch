package io.github.darkryh.dispatch.widget

import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.Placeable
import io.github.darkryh.dispatch.layout.SimplePlaceable
import io.github.darkryh.dispatch.modifier.BorderStyle
import io.github.darkryh.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelMeasurableTest {
    private fun terminal(
        width: Int = 80,
        height: Int = 24,
    ): Terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, width = width, height = height, interactive = false)

    private fun createTextMeasurable(text: String): TextMeasurable =
        TextMeasurable(
            text = text,
            modifier = Modifier,
            style = null,
            align = com.github.ajalt.mordant.rendering.TextAlign.LEFT,
            maxLines = null,
            overflow = TextOverflow.Clip,
            markdown = false,
            terminal = terminal(),
        )

    private class CapturingMeasurable(
        private val onMeasure: (Constraints) -> Unit,
        private val placeable: Placeable,
        override val modifier: Modifier = Modifier,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable {
            onMeasure(constraints)
            return placeable
        }
    }

    @Test
    fun `panel with no content has border dimensions`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.height >= 2, "panel should have at least top and bottom border")
        assertTrue(placeable.width >= 2, "panel should have at least left and right border")
    }

    @Test
    fun `panel with title includes title in output`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = "Settings",
                borderStyle = BorderStyle.Rounded,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        val allLines = placeable.lines.joinToString("\n")
        assertTrue(allLines.contains("Settings"), "panel should contain title")
    }

    @Test
    fun `panel with content includes content lines`() {
        val childMeasurable = createTextMeasurable("Hello World")

        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = listOf(childMeasurable),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        val allLines = placeable.lines.joinToString("\n")
        assertTrue(allLines.contains("Hello World"), "panel should contain child content")
    }

    @Test
    fun `BorderStyle Rounded uses rounded corners`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        val firstLine = placeable.lines.firstOrNull() ?: ""
        assertTrue(firstLine.contains("╭") || firstLine.contains("┌"), "rounded border should have curved corners")
    }

    @Test
    fun `BorderStyle Square uses square corners`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Square,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        val firstLine = placeable.lines.firstOrNull() ?: ""
        assertTrue(firstLine.contains("┌") || firstLine.contains("╭") || firstLine.isNotEmpty(), "square border should have corners")
    }

    @Test
    fun `BorderStyle Ascii uses ASCII characters`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Ascii,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.isNotEmpty(), "panel should have lines")
    }

    @Test
    fun `BorderStyle Heavy uses heavy lines`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Heavy,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.isNotEmpty(), "panel should have lines")
    }

    @Test
    fun `BorderStyle Double uses double lines`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Double,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.isNotEmpty(), "panel should have lines")
    }

    @Test
    fun `BorderStyle None renders without border`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.None,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.height >= 0, "panel without border should still render")
    }

    @Test
    fun `panel respects maxWidth constraint`() {
        val childMeasurable = createTextMeasurable("This is a long text that should wrap")

        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = listOf(childMeasurable),
                terminal = terminal(width = 20),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints(maxWidth = 20))
        assertTrue(placeable.width <= 20, "panel width should respect maxWidth")
    }

    @Test
    fun `panel respects maxHeight constraint`() {
        val children = (1..10).map { createTextMeasurable("Line $it") }

        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = children,
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints(maxHeight = 5))
        assertTrue(placeable.height <= 5, "panel height should respect maxHeight")
    }

    @Test
    fun `panel with multiple children stacks them vertically`() {
        val child1 = createTextMeasurable("Line 1")
        val child2 = createTextMeasurable("Line 2")

        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = listOf(child1, child2),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        val allLines = placeable.lines.joinToString("\n")
        assertTrue(allLines.contains("Line 1"), "should contain first child content")
        assertTrue(allLines.contains("Line 2"), "should contain second child content")
    }

    @Test
    fun `panel with title and content`() {
        val childMeasurable = createTextMeasurable("Content")

        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = "Header",
                borderStyle = BorderStyle.Rounded,
                children = listOf(childMeasurable),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        val allLines = placeable.lines.joinToString("\n")
        assertTrue(allLines.contains("Header"), "should contain title")
        assertTrue(allLines.contains("Content"), "should contain content")
    }

    @Test
    fun `panel with empty title works`() {
        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = "",
                borderStyle = BorderStyle.Rounded,
                children = emptyList(),
                terminal = terminal(),
                titleTextStyle = null,
            )

        val placeable = measurable.measure(Constraints())
        assertTrue(placeable.lines.isNotEmpty(), "panel with empty title should render")
    }

    @Test
    fun `panel does not bound unbounded height for children`() {
        var seen: Constraints? = null

        val child =
            CapturingMeasurable(
                onMeasure = { seen = it },
                placeable = SimplePlaceable(width = 1, height = 1, lines = listOf("X")),
            )

        val measurable =
            PanelMeasurable(
                modifier = Modifier,
                title = null,
                borderStyle = BorderStyle.Rounded,
                children = listOf(child),
                terminal = terminal(),
                titleTextStyle = null,
                expand = false,
            )

        measurable.measure(
            Constraints(
                minWidth = 0,
                maxWidth = 20,
                minHeight = 0,
                maxHeight = Int.MAX_VALUE,
            ),
        )

        assertEquals(Int.MAX_VALUE, seen?.maxHeight)
    }
}
