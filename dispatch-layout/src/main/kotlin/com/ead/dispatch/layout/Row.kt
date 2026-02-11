package com.ead.dispatch.layout

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.WeightModifier
import com.ead.dispatch.modifier.applyToConstraints

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
@Dispatchable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Dispatchable RowScope.() -> Unit,
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

        // Separate weighted and non-weighted children
        val weightedMeasurables = mutableListOf<Pair<Measurable, Float>>()
        val fixedMeasurables = mutableListOf<Measurable>()
        var totalWeight = 0f

        for (measurable in measurables) {
            val weightModifier =
                if (weightsEnabled) {
                    measurable.modifier.firstOrNull(WeightModifier::class.java)
                } else {
                    null
                }
            val weight = weightModifier?.weight
            if (weight != null) {
                weightedMeasurables.add(measurable to weight)
                totalWeight += weight
            } else {
                fixedMeasurables.add(measurable)
            }
        }

        // Measure fixed children first
        val fixedPlaceables = mutableListOf<Placeable>()
        var remainingWidthForFixed = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE

        for (measurable in fixedMeasurables) {
            val childConstraints =
                Constraints(
                    minWidth = 0,
                    maxWidth = remainingWidthForFixed,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                )
            val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
            val placeable = measurable.measure(modifiedConstraints)
            fixedPlaceables.add(placeable)

            if (constraints.hasBoundedWidth) {
                remainingWidthForFixed = (remainingWidthForFixed - placeable.width).coerceAtLeast(0)
            }
        }

        val remainingWidth = if (constraints.hasBoundedWidth) remainingWidthForFixed else Int.MAX_VALUE

        // Measure weighted children
        val weightedPlaceables = mutableListOf<Placeable>()
        if (weightsEnabled && totalWeight > 0) {
            for ((measurable, weight) in weightedMeasurables) {
                val weightedWidth = (remainingWidth * weight / totalWeight).toInt()
                val weightedConstraints =
                    Constraints(
                        minWidth = weightedWidth,
                        maxWidth = weightedWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight,
                    )
                val modifiedConstraints = measurable.modifier.applyToConstraints(weightedConstraints)
                weightedPlaceables.add(measurable.measure(modifiedConstraints))
            }
        }

        // Combine placeables in original order
        val allPlaceables = mutableListOf<Placeable>()
        var fixedIndex = 0
        var weightedIndex = 0

        for (measurable in measurables) {
            val weightModifier =
                if (weightsEnabled) {
                    measurable.modifier.firstOrNull(WeightModifier::class.java)
                } else {
                    null
                }
            if (weightModifier != null) {
                allPlaceables.add(weightedPlaceables[weightedIndex++])
            } else {
                allPlaceables.add(fixedPlaceables[fixedIndex++])
            }
        }

        // Calculate layout size
        val contentWidth = allPlaceables.sumOf { it.width }
        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.constrainWidth(contentWidth)
            } else {
                contentWidth
            }

        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                allPlaceables.maxOfOrNull { it.height } ?: 0
            }

        // Arrange children horizontally
        val sizes = allPlaceables.map { it.width }
        val positions = horizontalArrangement.arrange(layoutWidth, sizes)

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for ((index, placeable) in allPlaceables.withIndex()) {
                val x = positions.getOrElse(index) { 0 }
                val y = verticalAlignment.align(layoutHeight, placeable.height)
                placeable.placeAt(x, y)
            }
        }
    }
}
