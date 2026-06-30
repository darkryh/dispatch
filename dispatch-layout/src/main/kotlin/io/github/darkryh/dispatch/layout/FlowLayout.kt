package io.github.darkryh.dispatch.layout

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.applyToConstraints

/**
 * A layout that places children left-to-right, wrapping onto a new line whenever the next child
 * would overflow the available width.
 *
 * Children are packed greedily: a child stays on the current line while it still fits within
 * [Constraints.maxWidth] (accounting for the inter-item spacing implied by [horizontalArrangement])
 * and the line has not yet reached [maxItemsInEachRow]; otherwise it starts a fresh line. Each
 * wrapped line is as tall as its tallest child, and lines are stacked vertically using
 * [verticalArrangement]. Within a line, children are positioned using [horizontalArrangement].
 *
 * Terminal correctness: all positions and sizes are whole character cells. When the incoming width
 * is unbounded (`maxWidth == Int.MAX_VALUE`) wrapping by width is disabled and every child is laid
 * out on a single line, so the layout never assumes an infinite width and then tries to wrap inside
 * it. A single child wider than `maxWidth` is still given its own line and is clipped by the
 * rasterizer rather than corrupting neighbouring cells.
 *
 * Example:
 * ```kotlin
 * FlowRow(
 *     modifier = Modifier.fillMaxWidth(),
 *     horizontalArrangement = Arrangement.spacedBy(1),
 *     verticalArrangement = Arrangement.spacedBy(1),
 *     maxItemsInEachRow = 4,
 * ) {
 *     Text("alpha")
 *     Text("beta")
 *     Text("gamma")
 * }
 * ```
 *
 * @param modifier Modifiers to apply to this layout.
 * @param horizontalArrangement How to arrange children horizontally within each line.
 * @param verticalArrangement How to arrange the wrapped lines vertically.
 * @param maxItemsInEachRow The maximum number of children allowed on a single line.
 * @param content The content lambda containing children.
 */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        measurePolicy =
            FlowRowMeasurePolicy(
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = verticalArrangement,
                maxItemsInEachRow = maxItemsInEachRow,
            ),
        content = { FlowRowScopeInstance.content() },
    )
}

/**
 * A layout that places children top-to-bottom, wrapping into a new column whenever the next child
 * would overflow the available height.
 *
 * This is the transpose of [FlowRow]. Children are packed greedily down a column while they still
 * fit within [Constraints.maxHeight] (accounting for the inter-item spacing implied by
 * [verticalArrangement]) and the column has not yet reached [maxItemsInEachColumn]; otherwise a new
 * column is started. Each wrapped column is as wide as its widest child, and columns are stacked
 * horizontally using [horizontalArrangement]. Within a column, children are positioned using
 * [verticalArrangement].
 *
 * Terminal correctness: all positions and sizes are whole character cells. When the incoming height
 * is unbounded (`maxHeight == Int.MAX_VALUE`) wrapping by height is disabled and every child is laid
 * out in a single column, so the layout never assumes an infinite height and then tries to wrap
 * inside it. A single child taller than `maxHeight` is still given its own column and is clipped by
 * the rasterizer.
 *
 * Example:
 * ```kotlin
 * FlowColumn(
 *     modifier = Modifier.fillMaxHeight(),
 *     verticalArrangement = Arrangement.spacedBy(1),
 *     horizontalArrangement = Arrangement.spacedBy(2),
 *     maxItemsInEachColumn = 3,
 * ) {
 *     Text("one")
 *     Text("two")
 *     Text("three")
 * }
 * ```
 *
 * @param modifier Modifiers to apply to this layout.
 * @param verticalArrangement How to arrange children vertically within each column.
 * @param horizontalArrangement How to arrange the wrapped columns horizontally.
 * @param maxItemsInEachColumn The maximum number of children allowed in a single column.
 * @param content The content lambda containing children.
 */
@Composable
fun FlowColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    maxItemsInEachColumn: Int = Int.MAX_VALUE,
    content: @Composable FlowColumnScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        measurePolicy =
            FlowColumnMeasurePolicy(
                verticalArrangement = verticalArrangement,
                horizontalArrangement = horizontalArrangement,
                maxItemsInEachColumn = maxItemsInEachColumn,
            ),
        content = { FlowColumnScopeInstance.content() },
    )
}

/**
 * Scope for FlowRow content.
 */
interface FlowRowScope : LayoutScope

/**
 * Implementation of FlowRowScope.
 */
object FlowRowScopeInstance : FlowRowScope

/**
 * Scope for FlowColumn content.
 */
interface FlowColumnScope : LayoutScope

/**
 * Implementation of FlowColumnScope.
 */
object FlowColumnScopeInstance : FlowColumnScope

/**
 * Measure policy for [FlowRow].
 *
 * Children are measured against the available width, packed greedily into lines, and each line is
 * arranged independently along the horizontal axis while the lines themselves are arranged along the
 * vertical axis.
 */
internal class FlowRowMeasurePolicy(
    private val horizontalArrangement: Arrangement.Horizontal,
    private val verticalArrangement: Arrangement.Vertical,
    private val maxItemsInEachRow: Int,
) : MeasurePolicy {
    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    override fun measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return MeasureResult(
                width = constraints.minWidth,
                height = constraints.minHeight,
            )
        }

        val count = measurables.size

        // Measure every child once against the available space; widths/heights are cached in
        // primitive arrays indexed in original order to avoid re-reading placeables during packing.
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
        val placeables = arrayOfNulls<Placeable>(count)
        val widths = IntArray(count)
        val heights = IntArray(count)
        for (index in 0 until count) {
            val measurable = measurables[index]
            val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
            val placeable = measurable.measure(modifiedConstraints)
            placeables[index] = placeable
            widths[index] = placeable.width
            heights[index] = placeable.height
        }

        // Wrapping by width only makes sense with a bounded width; an unbounded width collapses to a
        // single line (we must not assume infinite space and then wrap inside it).
        val wrapByWidth = constraints.hasBoundedWidth
        val maxWidth = constraints.maxWidth
        val itemSpacing = horizontalArrangement.itemSpacing()
        val maxItems = maxItemsInEachRow.coerceAtLeast(1)

        // Greedy packing: walk children in order, breaking the contiguous run into lines. Children on
        // a line are always contiguous, so each line is recorded as a [start, end) index range.
        val lineStarts = ArrayList<Int>()
        val lineEnds = ArrayList<Int>()
        var lineStart = 0
        var lineWidth = 0
        var lineCount = 0
        for (index in 0 until count) {
            val childWidth = widths[index]
            if (lineCount == 0) {
                lineStart = index
                lineWidth = childWidth
                lineCount = 1
                continue
            }
            val fitsWidth = !wrapByWidth || lineWidth + itemSpacing + childWidth <= maxWidth
            val underItemCap = lineCount < maxItems
            if (fitsWidth && underItemCap) {
                lineWidth += itemSpacing + childWidth
                lineCount++
            } else {
                lineStarts.add(lineStart)
                lineEnds.add(index)
                lineStart = index
                lineWidth = childWidth
                lineCount = 1
            }
        }
        lineStarts.add(lineStart)
        lineEnds.add(count)

        // Per-line cross-axis size (height) and main-axis content size (width).
        val numLines = lineStarts.size
        val lineHeights = IntArray(numLines)
        var maxLineWidth = 0
        for (line in 0 until numLines) {
            var contentWidth = 0
            var tallest = 0
            var inLine = 0
            for (index in lineStarts[line] until lineEnds[line]) {
                if (inLine > 0) contentWidth += itemSpacing
                contentWidth += widths[index]
                if (heights[index] > tallest) tallest = heights[index]
                inLine++
            }
            lineHeights[line] = tallest
            if (contentWidth > maxLineWidth) maxLineWidth = contentWidth
        }

        // Reserve vertical room for the spacing the vertical arrangement inserts between lines.
        val lineSpacing = verticalArrangement.itemSpacing()
        var contentHeight = lineSpacing * (numLines - 1).coerceAtLeast(0)
        for (line in 0 until numLines) {
            contentHeight += lineHeights[line]
        }

        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.constrainWidth(maxLineWidth)
            } else {
                maxLineWidth
            }

        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.constrainHeight(contentHeight)
            } else {
                contentHeight
            }

        // Arrange the lines vertically; each line is then arranged horizontally during placement.
        val lineYs = verticalArrangement.arrange(layoutHeight, lineHeights.toList())

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for (line in 0 until numLines) {
                val start = lineStarts[line]
                val end = lineEnds[line]
                val sizes = ArrayList<Int>(end - start)
                for (index in start until end) {
                    sizes.add(widths[index])
                }
                val xs = horizontalArrangement.arrange(layoutWidth, sizes)
                val baseY = lineYs.getOrElse(line) { 0 }
                for (offset in 0 until end - start) {
                    val index = start + offset
                    val placeable = placeables[index] ?: continue
                    val x = xs.getOrElse(offset) { 0 }
                    // Children are top-aligned within their line (line height is the tallest child).
                    placeable.placeAt(x, baseY)
                }
            }
        }
    }
}

/**
 * Measure policy for [FlowColumn].
 *
 * The transpose of [FlowRowMeasurePolicy]: children are measured against the available height,
 * packed greedily into columns, and each column is arranged independently along the vertical axis
 * while the columns themselves are arranged along the horizontal axis.
 */
internal class FlowColumnMeasurePolicy(
    private val verticalArrangement: Arrangement.Vertical,
    private val horizontalArrangement: Arrangement.Horizontal,
    private val maxItemsInEachColumn: Int,
) : MeasurePolicy {
    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    override fun measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return MeasureResult(
                width = constraints.minWidth,
                height = constraints.minHeight,
            )
        }

        val count = measurables.size

        // Measure every child once against the available space; widths/heights are cached in
        // primitive arrays indexed in original order to avoid re-reading placeables during packing.
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
        val placeables = arrayOfNulls<Placeable>(count)
        val widths = IntArray(count)
        val heights = IntArray(count)
        for (index in 0 until count) {
            val measurable = measurables[index]
            val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
            val placeable = measurable.measure(modifiedConstraints)
            placeables[index] = placeable
            widths[index] = placeable.width
            heights[index] = placeable.height
        }

        // Wrapping by height only makes sense with a bounded height; an unbounded height collapses to
        // a single column (we must not assume infinite space and then wrap inside it).
        val wrapByHeight = constraints.hasBoundedHeight
        val maxHeight = constraints.maxHeight
        val itemSpacing = verticalArrangement.itemSpacing()
        val maxItems = maxItemsInEachColumn.coerceAtLeast(1)

        // Greedy packing: walk children in order, breaking the contiguous run into columns. Children
        // in a column are always contiguous, so each column is recorded as a [start, end) range.
        val columnStarts = ArrayList<Int>()
        val columnEnds = ArrayList<Int>()
        var columnStart = 0
        var columnHeight = 0
        var columnCount = 0
        for (index in 0 until count) {
            val childHeight = heights[index]
            if (columnCount == 0) {
                columnStart = index
                columnHeight = childHeight
                columnCount = 1
                continue
            }
            val fitsHeight = !wrapByHeight || columnHeight + itemSpacing + childHeight <= maxHeight
            val underItemCap = columnCount < maxItems
            if (fitsHeight && underItemCap) {
                columnHeight += itemSpacing + childHeight
                columnCount++
            } else {
                columnStarts.add(columnStart)
                columnEnds.add(index)
                columnStart = index
                columnHeight = childHeight
                columnCount = 1
            }
        }
        columnStarts.add(columnStart)
        columnEnds.add(count)

        // Per-column cross-axis size (width) and main-axis content size (height).
        val numColumns = columnStarts.size
        val columnWidths = IntArray(numColumns)
        var maxColumnHeight = 0
        for (column in 0 until numColumns) {
            var contentHeight = 0
            var widest = 0
            var inColumn = 0
            for (index in columnStarts[column] until columnEnds[column]) {
                if (inColumn > 0) contentHeight += itemSpacing
                contentHeight += heights[index]
                if (widths[index] > widest) widest = widths[index]
                inColumn++
            }
            columnWidths[column] = widest
            if (contentHeight > maxColumnHeight) maxColumnHeight = contentHeight
        }

        // Reserve horizontal room for the spacing the horizontal arrangement inserts between columns.
        val columnSpacing = horizontalArrangement.itemSpacing()
        var contentWidth = columnSpacing * (numColumns - 1).coerceAtLeast(0)
        for (column in 0 until numColumns) {
            contentWidth += columnWidths[column]
        }

        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.constrainWidth(contentWidth)
            } else {
                contentWidth
            }

        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.constrainHeight(maxColumnHeight)
            } else {
                maxColumnHeight
            }

        // Arrange the columns horizontally; each column is then arranged vertically during placement.
        val columnXs = horizontalArrangement.arrange(layoutWidth, columnWidths.toList())

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for (column in 0 until numColumns) {
                val start = columnStarts[column]
                val end = columnEnds[column]
                val sizes = ArrayList<Int>(end - start)
                for (index in start until end) {
                    sizes.add(heights[index])
                }
                val ys = verticalArrangement.arrange(layoutHeight, sizes)
                val baseX = columnXs.getOrElse(column) { 0 }
                for (offset in 0 until end - start) {
                    val index = start + offset
                    val placeable = placeables[index] ?: continue
                    val y = ys.getOrElse(offset) { 0 }
                    // Children are start-aligned within their column (column width is the widest child).
                    placeable.placeAt(baseX, y)
                }
            }
        }
    }
}

/**
 * The fixed gap a horizontal [Arrangement] inserts between two adjacent items.
 *
 * Probing the arrangement with two zero-size items in a zero-size container isolates the fixed
 * inter-item spacing: gap-free arrangements (Start, End, Center, SpaceBetween, SpaceAround,
 * SpaceEvenly) report `0`, while [Arrangement.SpacedBy] reports its spacing. This value is used to
 * make the greedy wrapping decision agree with how the line is later positioned, using only the
 * public `arrange` API.
 */
private fun Arrangement.Horizontal.itemSpacing(): Int {
    val probe = arrange(0, ZERO_PAIR)
    return (probe.getOrElse(1) { 0 } - probe.getOrElse(0) { 0 }).coerceAtLeast(0)
}

/**
 * The fixed gap a vertical [Arrangement] inserts between two adjacent items.
 *
 * @see itemSpacing for the horizontal counterpart and the probing rationale.
 */
private fun Arrangement.Vertical.itemSpacing(): Int {
    val probe = arrange(0, ZERO_PAIR)
    return (probe.getOrElse(1) { 0 } - probe.getOrElse(0) { 0 }).coerceAtLeast(0)
}

/** Two zero-size items used to probe an arrangement's fixed inter-item spacing. */
private val ZERO_PAIR = listOf(0, 0)
