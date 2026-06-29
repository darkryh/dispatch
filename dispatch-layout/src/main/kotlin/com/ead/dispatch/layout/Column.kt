package com.ead.dispatch.layout

import androidx.compose.runtime.Composable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.WeightModifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.modifier.getOffset

/**
 * A layout that places children vertically, one below the other.
 *
 * Example:
 * ```kotlin
 * Column(
 *     modifier = Modifier.fillMaxSize(),
 *     verticalArrangement = Arrangement.SpaceBetween,
 *     horizontalAlignment = Alignment.CenterHorizontally,
 * ) {
 *     Text("Header")
 *     Text("Body")
 *     Text("Footer")
 * }
 * ```
 *
 * @param modifier Modifiers to apply to this layout.
 * @param verticalArrangement How to arrange children vertically.
 * @param horizontalAlignment How to align children horizontally.
 * @param content The content lambda containing children.
 */
@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = ColumnScopeInstance(horizontalAlignment)
    Layout(
        modifier = modifier,
        measurePolicy = ColumnMeasurePolicy(verticalArrangement, horizontalAlignment),
        content = { scope.content() },
    )
}

/**
 * Scope for Column content.
 */
interface ColumnScope : LayoutScope {
    /**
     * The horizontal alignment for children.
     */
    val horizontalAlignment: Alignment.Horizontal
}

/**
 * Implementation of ColumnScope.
 */
data class ColumnScopeInstance(
    override val horizontalAlignment: Alignment.Horizontal,
) : ColumnScope

/**
 * Measure policy for Column layout.
 */
internal class ColumnMeasurePolicy(
    private val verticalArrangement: Arrangement.Vertical,
    private val horizontalAlignment: Alignment.Horizontal,
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

        // Weight only makes sense when the parent provides a bounded height; otherwise we'd try to
        // allocate "remaining space" from an infinite height and end up with absurd child sizes.
        val weightsEnabled = constraints.hasBoundedHeight

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

        // Measure fixed children first
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )

        var fixedHeight = 0
        for (index in 0 until count) {
            if (!weights[index].isNaN()) continue
            val measurable = measurables[index]
            val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
            val placeable = measurable.measure(modifiedConstraints)
            placeables[index] = placeable
            fixedHeight += placeable.height
        }

        val remainingHeight = (constraints.maxHeight - fixedHeight).coerceAtLeast(0)

        // Measure weighted children
        if (weightsEnabled && totalWeight > 0) {
            for (index in 0 until count) {
                val weight = weights[index]
                if (weight.isNaN()) continue
                val weightedHeight = (remainingHeight * weight / totalWeight).toInt()
                val weightedConstraints =
                    Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth,
                        minHeight = weightedHeight,
                        maxHeight = weightedHeight,
                    )
                val measurable = measurables[index]
                val modifiedConstraints = measurable.modifier.applyToConstraints(weightedConstraints)
                placeables[index] = measurable.measure(modifiedConstraints)
            }
        }

        // Sizes in original order; weighted children that were skipped (totalWeight == 0) stay null.
        val sizes = IntArray(count)
        var contentHeight = 0
        var maxWidth = 0
        for (index in 0 until count) {
            val placeable = placeables[index] ?: continue
            sizes[index] = placeable.height
            contentHeight += placeable.height
            if (placeable.width > maxWidth) maxWidth = placeable.width
        }

        // Calculate layout size
        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                maxWidth
            }

        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.constrainHeight(contentHeight)
            } else {
                contentHeight
            }

        // Arrange children vertically
        val positions = verticalArrangement.arrange(layoutHeight, sizes.toList())

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for (index in 0 until count) {
                val placeable = placeables[index] ?: continue
                // A per-child Modifier.align(...) overrides the column's horizontalAlignment for
                // this child only; absent one, the container alignment is used (default unchanged).
                val childModifier = measurables[index].modifier
                val horizontal = childModifier.columnAlign() ?: horizontalAlignment
                var x = horizontal.align(layoutWidth, placeable.width)
                var y = positions.getOrElse(index) { 0 }
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
