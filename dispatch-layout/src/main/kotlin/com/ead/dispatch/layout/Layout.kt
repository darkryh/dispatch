package com.ead.dispatch.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.runtime.DispatchNodeApplier

/**
 * Base layout component that measures and places children according to a policy.
 *
 * This is the foundation for all layout containers like Column, Row, and Box.
 *
 * @param modifier Modifiers to apply to this layout.
 * @param measurePolicy The policy for measuring and placing children.
 * @param content The content lambda containing children.
 */
@Composable
fun Layout(
    modifier: Modifier = Modifier,
    measurePolicy: MeasurePolicy,
    content: @Composable () -> Unit,
) {
    ComposeNode<LayoutNode, DispatchNodeApplier>(
        factory = { LayoutNode("Layout") },
        update = {
            set(modifier to measurePolicy) { (nextModifier, nextPolicy) ->
                setDelegate(
                    LayoutMeasurable(
                        modifier = nextModifier,
                        measurePolicy = nextPolicy,
                        children = { children },
                    )
                )
            }
        },
        content = content,
    )
}

/**
 * A measurable that represents a layout container.
 */
internal class LayoutMeasurable(
    override val modifier: Modifier,
    private val measurePolicy: MeasurePolicy,
    private val children: () -> List<Measurable>,
) : Measurable {
    private class LineCanvas(
        val width: Int,
    ) {
        private val chars: CharArray = CharArray(width) { ' ' }
        private val inserts: Array<StringBuilder?> = arrayOfNulls(width + 1)

        private var hasAnsi: Boolean = false

        fun setCharAt(
            column: Int,
            value: Char,
        ) {
            if (column !in 0 until width) return
            chars[column] = value
        }

        fun insertAt(
            column: Int,
            sequence: CharSequence,
        ) {
            val safeColumn = column.coerceIn(0, width)
            val sb = inserts[safeColumn] ?: StringBuilder().also { inserts[safeColumn] = it }
            sb.append(sequence)
            hasAnsi = true
        }

        fun buildLine(): String {
            if (width == 0) return ""

            val extraLength = if (hasAnsi) inserts.sumOf { it?.length ?: 0 } else 0
            val sb = StringBuilder(width + extraLength + 8)
            for (col in 0 until width) {
                inserts[col]?.let(sb::append)
                sb.append(chars[col])
            }
            inserts[width]?.let(sb::append)

            // Ensure ANSI state doesn't leak to subsequent lines if any child output included ANSI.
            if (hasAnsi) sb.append(ANSI_RESET)

            return sb.toString()
        }
    }

    override fun measure(constraints: Constraints): Placeable {
        // Measure all children and get result
        val result = measurePolicy.measure(children(), constraints)

        // Render children to lines
        val lines = renderChildren(result, constraints)

        return SimplePlaceable(
            width = result.width,
            height = result.height,
            lines = lines,
        )
    }

    @Suppress("CognitiveComplexMethod", "LoopWithTooManyJumpStatements")
    private fun renderChildren(
        result: MeasureResult,
        constraints: Constraints,
    ): List<String> {
        // Execute placement to get child positions
        val scope = SimplePlacementScope()
        result.placementBlock(scope)
        val placements = scope.getPlacements()

        // Create a canvas
        val width = result.width.coerceIn(0, constraints.maxWidth)
        val height = result.height.coerceIn(0, constraints.maxHeight)

        // A width of 0 can still represent vertical space (e.g. `Spacer(Modifier.height(n))`).
        // Return `height` blank lines so parents like LazyColumn can include that spacing.
        if (height == 0) {
            return emptyList()
        }

        // Initialize a canvas of fixed visible width.
        // ANSI codes are treated as zero-width and inserted separately so they don't shift columns.
        val canvas = Array(height) { LineCanvas(width) }

        // Place each child
        for ((placeable, position) in placements) {
            val (x, y) = position
            for ((lineIndex, line) in placeable.lines.withIndex()) {
                val targetY = y + lineIndex
                if (targetY !in 0 until height) continue
                if (placeable.width == 0) continue
                if (x >= width) continue
                if (x + placeable.width <= 0) continue
                paintLine(canvas[targetY], x, line)
            }
        }

        return canvas.map { it.buildLine() }
    }

    private fun paintLine(
        canvas: LineCanvas,
        startColumn: Int,
        line: String,
    ) {
        if (canvas.width == 0) return

        var column = startColumn
        val pending = StringBuilder()
        var painted = false

        var index = 0
        while (index < line.length) {
            val c = line[index]
            if (c == '\u001B') {
                val sequenceLength = ansiSequenceLength(line, index)
                if (sequenceLength > 0) {
                    pending.append(line, index, index + sequenceLength)
                    index += sequenceLength
                    continue
                }
            }

            if (column in 0 until canvas.width) {
                if (pending.isNotEmpty()) {
                    canvas.insertAt(column, pending)
                    pending.clear()
                }
                canvas.setCharAt(column, c)
                painted = true
            }

            column++
            index++
        }

        if (painted && pending.isNotEmpty()) {
            canvas.insertAt(column, pending)
        }
    }

    private companion object {
        private const val ANSI_RESET = "\u001B[0m"

        /**
         * Return the length of the ANSI escape sequence starting at [start], or 0 if none is found.
         *
         * Supported sequences:
         * - CSI: ESC `[` ... <final byte>
         * - OSC: ESC `]` ... BEL or ESC `\\`
         * - Single-char escapes: ESC <char>
         */
        private fun ansiSequenceLength(
            text: String,
            start: Int,
        ): Int {
            if (start !in text.indices || text[start] != '\u001B') return 0
            if (start + 1 !in text.indices) return 0

            return when (text[start + 1]) {
                '[' -> parseCsi(text, start)
                ']' -> parseOsc(text, start)
                else -> 2
            }
        }

        private fun parseCsi(
            text: String,
            start: Int,
        ): Int {
            var i = start + 2
            while (i < text.length) {
                val c = text[i]
                // CSI sequences end with a final byte in the range 0x40..0x7E.
                if (c in '@'..'~') return i - start + 1
                i++
            }
            return 0
        }

        private fun parseOsc(
            text: String,
            start: Int,
        ): Int {
            var i = start + 2
            while (i < text.length) {
                val c = text[i]
                if (c == '\u0007') return i - start + 1 // BEL
                if (c == '\u001B' && i + 1 < text.length && text[i + 1] == '\\') {
                    return i - start + 2
                }
                i++
            }
            return 0
        }
    }
}

/**
 * Scope for building layout content.
 */
interface LayoutScope {
    /**
     * The modifier to apply to items in this layout.
     */
    val itemModifier: Modifier get() = Modifier
}

/**
 * Base layout scope implementation.
 */
open class LayoutScopeInstance : LayoutScope
