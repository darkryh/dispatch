package com.ead.dispatch.layout

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints

/**
 * A layout that stacks children on top of each other.
 *
 * Children are placed in order, with later children drawn on top.
 * The default alignment is top-left.
 *
 * Example:
 * ```kotlin
 * Box(
 *     modifier = Modifier.fillMaxSize(),
 *     contentAlignment = Alignment.Center,
 * ) {
 *     Panel(title = "Background")
 *     Text("Foreground")
 * }
 * ```
 *
 * @param modifier Modifiers to apply to this layout.
 * @param contentAlignment Default alignment for children.
 * @param content The content lambda containing children.
 */
@Dispatchable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment.Alignment2D = Alignment.TopStart,
    content: @Dispatchable BoxScope.() -> Unit,
) {
    val scope = BoxScopeInstance(contentAlignment)
    Layout(
        modifier = modifier,
        measurePolicy = BoxMeasurePolicy(contentAlignment),
        content = { scope.content() }
    )
}

/**
 * Scope for Box content.
 */
interface BoxScope : LayoutScope {
    /**
     * The default content alignment.
     */
    val contentAlignment: Alignment.Alignment2D
}

/**
 * Implementation of BoxScope.
 */
class BoxScopeInstance(
    override val contentAlignment: Alignment.Alignment2D,
) : BoxScope

/**
 * Measure policy for Box layout.
 */
internal class BoxMeasurePolicy(
    private val contentAlignment: Alignment.Alignment2D,
) : MeasurePolicy {

    override fun measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (measurables.isEmpty()) {
            return MeasureResult(
                width = constraints.minWidth,
                height = constraints.minHeight,
            )
        }

        // Measure all children
        val childConstraints = Constraints(
            minWidth = 0,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight,
        )

        val placeables = measurables.map { measurable ->
            val modifiedConstraints = measurable.modifier.applyToConstraints(childConstraints)
            measurable.measure(modifiedConstraints)
        }

        // Calculate layout size (largest child)
        val contentWidth = placeables.maxOfOrNull { it.width } ?: 0
        val contentHeight = placeables.maxOfOrNull { it.height } ?: 0

        val layoutWidth = if (constraints.hasBoundedWidth) {
            constraints.constrainWidth(contentWidth)
        } else {
            contentWidth
        }

        val layoutHeight = if (constraints.hasBoundedHeight) {
            constraints.constrainHeight(contentHeight)
        } else {
            contentHeight
        }

        return MeasureResult(
            width = layoutWidth,
            height = layoutHeight,
        ) {
            for (placeable in placeables) {
                val (x, y) = contentAlignment.align(
                    containerWidth = layoutWidth,
                    containerHeight = layoutHeight,
                    contentWidth = placeable.width,
                    contentHeight = placeable.height,
                )
                placeable.placeAt(x, y)
            }
        }
    }
}

/**
 * Empty Box - useful as a spacer.
 */
@Dispatchable
fun Spacer(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {}
}
