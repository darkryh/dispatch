package io.github.darkryh.dispatch.layout

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.WeightModifier
import io.github.darkryh.dispatch.modifier.applyToConstraints
import io.github.darkryh.dispatch.modifier.getOffset

/**
 * A layout that places children horizontally, side by side.
 *
 * Example:
 * ```kotlin
 * Row(
 *     modifier = Modifier.fillMaxWidth(),
 *     horizontalArrangement = Arrangement.SpaceBetween,
 *     verticalAlignment = Alignment.CenterVertically,
 * ) {
 *     Text("Left")
 *     Text("Right")
 * }
 * ```
 *
 * @param modifier Modifiers to apply to this layout.
 * @param horizontalArrangement How to arrange children horizontally.
 * @param verticalAlignment How to align children vertically.
 * @param content The content lambda containing children.
 */
@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    val scope = RowScopeInstance(verticalAlignment)
    Layout(
        modifier = modifier,
        measurePolicy = RowMeasurePolicy(horizontalArrangement, verticalAlignment),
        content = { scope.content() },
    )
}

/**
 * Scope for Row content.
 */
interface RowScope : LayoutScope {
    /**
     * The vertical alignment for children.
     */
    val verticalAlignment: Alignment.Vertical
}

/**
 * Implementation of RowScope.
 */
data class RowScopeInstance(
    override val verticalAlignment: Alignment.Vertical,
) : RowScope

/**
 * Measure policy for Row layout.
 */
internal class RowMeasurePolicy(
    private val horizontalArrangement: Arrangement.Horizontal,
    private val verticalAlignment: Alignment.Vertical,
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

        // Weight only makes sense when the parent provides a bounded width; otherwise we'd try to
        // allocate "remaining space" from an infinite width and end up with absurd child sizes.
        val weightsEnabled = constraints.hasBoundedWidth

        // Resolve each child's weight exactly once into a parallel array (no Pair boxing, no second
        // modifier scan). Placeables are written into a single array indexed in original order.
        val count = measurables.size
        // Primitive FloatArray (no per-element boxing) with NaN as the "no weight" sentinel.
        // Real weights are always > 0 (WeightModifier requires it), so NaN can never collide.
        val weights = FloatArray(count) { Float.NaN }
        var totalWeight = 0f

        for (index in 0 until count) {
            val weight =
                if (weightsEnabled) {
                    measurables[index].modifier.firstOrNull(WeightModifier::class.java)?.weight
                } else {
                    null
                }
            if (weight != null) {
                weights[index] = weight
                totalWeight += weight
            }
        }

        val placeables = arrayOfNulls<Placeable>(count)

        // Fixed inter-child gaps inserted by the arrangement (e.g. spacedBy). They occupy real
        // columns, so they must join the reported width and be reserved out of the width budget;
        // otherwise the row under-reports and whatever follows paints over the last child.
        val gapTotal = (count - 1).coerceAtLeast(0) * horizontalArrangement.spacing

        // Measure fixed children first
        var remainingWidthForFixed =
            if (constraints.hasBoundedWidth) {
                (constraints.maxWidth - gapTotal).coerceAtLeast(0)
            } else {
                Int.MAX_VALUE
            }

        for (index in 0 until count) {
            if (!weights[index].isNaN()) continue
            val measurable = measurables[index]
            val childConstraints =
                Constraints(
                    minWidth = 0,
                    maxWidth = remainingWidthForFixed,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                )
            val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
            val placeable = measurable.measure(modifiedConstraints)
            placeables[index] = placeable

            if (constraints.hasBoundedWidth) {
                remainingWidthForFixed = (remainingWidthForFixed - placeable.width).coerceAtLeast(0)
            }
        }

        val remainingWidth = if (constraints.hasBoundedWidth) remainingWidthForFixed else Int.MAX_VALUE

        // Measure weighted children
        if (weightsEnabled && totalWeight > 0) {
            for (index in 0 until count) {
                val weight = weights[index]
                if (weight.isNaN()) continue
                val weightedWidth = (remainingWidth * weight / totalWeight).toInt()
                val weightedConstraints =
                    Constraints(
                        minWidth = weightedWidth,
                        maxWidth = weightedWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight,
                    )
                val measurable = measurables[index]
                val modifiedConstraints = measurable.modifier.applyToConstraints(weightedConstraints)
                placeables[index] = measurable.measure(modifiedConstraints)
            }
        }

        // Sizes in original order.
        val sizes = IntArray(count)
        var contentWidth = 0
        var maxHeight = 0
        for (index in 0 until count) {
            val placeable = placeables[index] ?: continue
            sizes[index] = placeable.width
            contentWidth += placeable.width
            if (placeable.height > maxHeight) maxHeight = placeable.height
        }

        // Calculate layout size
        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.constrainWidth(contentWidth + gapTotal)
            } else {
                contentWidth + gapTotal
            }

        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                maxHeight
            }

        // Arrange children horizontally
        val positions = horizontalArrangement.arrange(layoutWidth, sizes.toList())

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for (index in 0 until count) {
                val placeable = placeables[index] ?: continue
                // A per-child Modifier.align(...) overrides the row's verticalAlignment for this
                // child only; absent one, the container alignment is used (default unchanged).
                val childModifier = measurables[index].modifier
                val vertical = childModifier.rowAlign() ?: verticalAlignment
                var x = positions.getOrElse(index) { 0 }
                var y = vertical.align(layoutHeight, placeable.height)
                // Apply an optional fixed cell offset after the normal position is computed.
                val offset = childModifier.getOffset()
                if (offset != null) {
                    x += offset.x
                    y += offset.y
                }
                placeable.placeAt(x, y)
            }
        }
    }
}
