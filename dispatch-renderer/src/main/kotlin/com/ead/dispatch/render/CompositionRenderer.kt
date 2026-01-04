package com.ead.dispatch.render

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.modifier.BorderModifierElement
import com.ead.dispatch.modifier.BorderCharacters
import com.ead.dispatch.modifier.BorderStyle
import com.ead.dispatch.modifier.PaddingValues
import com.ead.dispatch.modifier.getPadding
import com.ead.dispatch.modifier.getBorder
import com.ead.dispatch.modifier.Modifier

/**
 * Renders composition output to a list of strings.
 *
 * This class bridges the composition system with the terminal renderer,
 * measuring and placing content within terminal constraints.
 */
class CompositionRenderer(
    private val terminalWidth: Int,
    private val terminalHeight: Int,
) {
    /**
     * Root constraints based on terminal size.
     */
    val rootConstraints = Constraints(
        minWidth = 0,
        maxWidth = terminalWidth,
        minHeight = 0,
        maxHeight = terminalHeight,
    )

    /**
     * Render a measurable tree to lines.
     */
    fun render(root: Measurable): List<String> {
        // Measure the root with terminal constraints
        val placeable = root.measure(rootConstraints)

        // Apply decorations (border, padding)
        return applyDecorations(placeable, root.modifier)
    }

    /**
     * Apply decorations like borders and padding to the rendered content.
     */
    private fun applyDecorations(placeable: Placeable, modifier: Modifier): List<String> {
        var lines = placeable.lines.toMutableList()

        // Apply padding first (inside border)
        val padding = modifier.getPadding()
        if (padding != null) {
            lines = applyPadding(lines, padding, placeable.width)
        }

        // Apply border last (outside padding)
        val border = modifier.getBorder()
        if (border != null && border.style != BorderStyle.None) {
            lines = applyBorder(lines, border)
        }

        return lines
    }

    /**
     * Apply padding to content.
     */
    private fun applyPadding(
        lines: List<String>,
        padding: PaddingValues,
        contentWidth: Int,
    ): MutableList<String> {
        val paddedWidth = contentWidth + padding.start + padding.end
        val paddedLines = mutableListOf<String>()

        // Top padding
        repeat(padding.top) {
            paddedLines.add(" ".repeat(paddedWidth))
        }

        // Content with horizontal padding
        val leftPad = " ".repeat(padding.start)
        val rightPad = " ".repeat(padding.end)
        for (line in lines) {
            val paddedLine = leftPad + line.padEnd(contentWidth) + rightPad
            paddedLines.add(paddedLine)
        }

        // Bottom padding
        repeat(padding.bottom) {
            paddedLines.add(" ".repeat(paddedWidth))
        }

        return paddedLines
    }

    /**
     * Apply border to content.
     */
    private fun applyBorder(
        lines: List<String>,
        border: BorderModifierElement,
    ): MutableList<String> {
        val chars = BorderCharacters.forStyle(border.style)
        val contentWidth = lines.maxOfOrNull { it.length } ?: 0

        val borderedLines = mutableListOf<String>()

        // Top border
        val topBorder = if (border.title != null) {
            val title = border.title!!
            val titleWithPadding = " $title "
            val remainingWidth = contentWidth - titleWithPadding.length
            val leftLength = remainingWidth / 2
            val rightLength = remainingWidth - leftLength

            buildString {
                append(chars.topLeft)
                append(chars.horizontal.toString().repeat(leftLength.coerceAtLeast(0)))
                append(titleWithPadding)
                append(chars.horizontal.toString().repeat(rightLength.coerceAtLeast(0)))
                append(chars.topRight)
            }
        } else {
            "${chars.topLeft}${chars.horizontal.toString().repeat(contentWidth)}${chars.topRight}"
        }
        borderedLines.add(topBorder)

        // Content with side borders
        for (line in lines) {
            borderedLines.add("${chars.vertical}${line.padEnd(contentWidth)}${chars.vertical}")
        }

        // Bottom border
        val bottomBorder = "${chars.bottomLeft}${chars.horizontal.toString().repeat(contentWidth)}${chars.bottomRight}"
        borderedLines.add(bottomBorder)

        return borderedLines
    }

    /**
     * Render raw lines (no measurable tree).
     */
    fun renderLines(lines: List<String>): List<String> {
        // Truncate lines to fit terminal
        return lines.take(terminalHeight).map { line ->
            if (line.length > terminalWidth) line.take(terminalWidth) else line
        }
    }

    /**
     * Create an empty frame.
     */
    fun emptyFrame(): List<String> {
        return List(terminalHeight) { " ".repeat(terminalWidth) }
    }

    /**
     * Fill remaining space with empty lines.
     */
    fun padToHeight(lines: List<String>): List<String> {
        if (lines.size >= terminalHeight) return lines.take(terminalHeight)

        val result = lines.toMutableList()
        val emptyLine = " ".repeat(terminalWidth)
        repeat(terminalHeight - lines.size) {
            result.add(emptyLine)
        }
        return result
    }
}

/**
 * Render context passed through the composition tree.
 */
data class RenderContext(
    /**
     * Current constraints for this node.
     */
    val constraints: Constraints,

    /**
     * X offset from parent.
     */
    val offsetX: Int = 0,

    /**
     * Y offset from parent.
     */
    val offsetY: Int = 0,

    /**
     * Whether the node is visible.
     */
    val visible: Boolean = true,

    /**
     * Clipping bounds (if any).
     */
    val clipBounds: ClipBounds? = null,
)

/**
 * Clipping bounds for scrollable content.
 */
data class ClipBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(px: Int, py: Int): Boolean =
        px in x until (x + width) && py in y until (y + height)
}
