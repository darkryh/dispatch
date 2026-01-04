package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.BorderCharacters
import com.ead.dispatch.modifier.BorderStyle
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.LocalTerminal
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel as MordantPanel
import com.github.ajalt.mordant.widgets.Text as MordantText

/**
 * A bordered panel with optional title.
 *
 * Example:
 * ```kotlin
 * Panel(title = "Settings") {
 *     Column {
 *         Text("Option 1")
 *         Text("Option 2")
 *     }
 * }
 * ```
 *
 * @param modifier Modifiers to apply.
 * @param title Optional title displayed in the top border.
 * @param borderStyle Style of the border.
 * @param titleStyle Style for the title text.
 * @param content Content inside the panel.
 */
@Dispatchable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    borderStyle: BorderStyle = BorderStyle.Rounded,
    titleStyle: TextStyle? = null,
    expand: Boolean = false,
    content: @Dispatchable () -> Unit,
) {
    val composer = Composer.current
    val terminal = LocalTerminal.current
    composer.startNode("Panel")

    // Collect child measurables
    val childMeasurables = mutableListOf<Measurable>()
    val previousCollector = composer.getMeasurableCollector()
    composer.setMeasurableCollector { measurable ->
        childMeasurables.add(measurable)
    }

    content()

    composer.setMeasurableCollector(previousCollector)

    val panelMeasurable = PanelMeasurable(
        modifier = modifier,
        title = title,
        borderStyle = borderStyle,
        children = childMeasurables,
        terminal = terminal,
        titleTextStyle = titleStyle,
        expand = expand,
    )

    composer.registerMeasurable(panelMeasurable)
    composer.endNode()
}

/**
 * Measurable implementation for Panel.
 */
internal class PanelMeasurable(
    override val modifier: Modifier,
    private val title: String?,
    private val borderStyle: BorderStyle,
    private val children: List<Measurable>,
    private val terminal: com.github.ajalt.mordant.terminal.Terminal,
    private val titleTextStyle: TextStyle?,
    private val expand: Boolean = false,
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        // Measure children within available space
        val innerMaxWidth = if (modifiedConstraints.hasBoundedWidth) {
            (modifiedConstraints.maxWidth - 2).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }

        val innerMaxHeight = if (modifiedConstraints.hasBoundedHeight) {
            (modifiedConstraints.maxHeight - 2).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }

        val innerMinWidth = (modifiedConstraints.minWidth - 2)
            .coerceAtLeast(0)
            .coerceAtMost(innerMaxWidth)

        val innerMinHeight = (modifiedConstraints.minHeight - 2)
            .coerceAtLeast(0)
            .coerceAtMost(innerMaxHeight)

        val innerConstraints = Constraints(
            minWidth = innerMinWidth,
            maxWidth = innerMaxWidth,
            minHeight = innerMinHeight,
            maxHeight = innerMaxHeight,
        )
        val childPlaceables = children.map { measurable ->
            measurable.measure(measurable.modifier.applyToConstraints(innerConstraints))
        }

        // Build Mordant content preserving layout
        val contentLines = childPlaceables.flatMap { it.lines }
        val contentWidget = MordantText(
            contentLines.joinToString("\n"),
            whitespace = Whitespace.PRE,
        )

        val panel = MordantPanel(
            content = contentWidget,
            title = title?.let { MordantText(it, whitespace = Whitespace.PRE) },
            borderType = borderType(borderStyle),
            padding = Padding(0),
            expand = expand,
            borderStyle = titleTextStyle,
        )

        val renderWidth = if (modifiedConstraints.hasBoundedWidth) {
            modifiedConstraints.maxWidth
        } else {
            10_000
        }

        val renderedLines = panel.render(terminal, width = renderWidth)
        val renderedWidth = renderedLines.width
        val rendered = terminal.render(renderedLines)

        val lines = rendered
            .lines()
            .let { output ->
                if (modifiedConstraints.hasBoundedHeight) {
                    output.take(modifiedConstraints.maxHeight)
                } else {
                    output
                }
            }

        return SimplePlaceable(
            width = modifiedConstraints.constrainWidth(renderedWidth),
            height = modifiedConstraints.constrainHeight(lines.size),
            lines = lines,
        )
    }
}

private fun borderType(style: BorderStyle): BorderType? = when (style) {
    BorderStyle.None -> null
    BorderStyle.Ascii -> BorderType.ASCII
    BorderStyle.Rounded -> BorderType.ROUNDED
    BorderStyle.Square -> BorderType.SQUARE
    BorderStyle.Heavy -> BorderType.HEAVY
    BorderStyle.Double -> BorderType.DOUBLE
    BorderStyle.Dashed -> BorderType.ASCII // closest available
}


/**
 * A simple card widget (panel with padding).
 */
@Dispatchable
fun Card(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Dispatchable () -> Unit,
) {
    Panel(
        modifier = modifier,
        title = title,
        borderStyle = BorderStyle.Rounded,
        content = content,
    )
}

/**
 * A section with a header line.
 */
@Dispatchable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Dispatchable () -> Unit,
) {
    Column(modifier = modifier) {
        Text("─── $title ───")
        content()
    }
}
