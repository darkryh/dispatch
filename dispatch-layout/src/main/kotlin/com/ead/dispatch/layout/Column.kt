package com.ead.dispatch.layout

import androidx.compose.runtime.Composable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.WeightModifier
import com.ead.dispatch.modifier.applyToConstraints

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
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )

        val fixedPlaceables =
            fixedMeasurables.map { measurable ->
                val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
                measurable.measure(modifiedConstraints)
            }

        val fixedHeight = fixedPlaceables.sumOf { it.height }
        val remainingHeight = (constraints.maxHeight - fixedHeight).coerceAtLeast(0)

        // Measure weighted children
        val weightedPlaceables = mutableListOf<Placeable>()
        if (weightsEnabled && totalWeight > 0) {
            for ((measurable, weight) in weightedMeasurables) {
                val weightedHeight = (remainingHeight * weight / totalWeight).toInt()
                val weightedConstraints =
                    Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth,
                        minHeight = weightedHeight,
                        maxHeight = weightedHeight,
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
        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                allPlaceables.maxOfOrNull { it.width } ?: 0
            }

        val contentHeight = allPlaceables.sumOf { it.height }
        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.constrainHeight(contentHeight)
            } else {
                contentHeight
            }

        // Arrange children vertically
        val sizes = allPlaceables.map { it.height }
        val positions = verticalArrangement.arrange(layoutHeight, sizes)

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for ((index, placeable) in allPlaceables.withIndex()) {
                val x = horizontalAlignment.align(layoutWidth, placeable.width)
                val y = positions.getOrElse(index) { 0 }
                placeable.placeAt(x, y)
            }
        }
    }
}
