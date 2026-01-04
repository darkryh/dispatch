package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier

/**
 * A component that can be measured within constraints.
 *
 * This is the core interface for the measure-layout two-pass system.
 */
interface Measurable {
    /**
     * The modifier chain applied to this measurable.
     */
    val modifier: Modifier

    /**
     * Measure this component within the given constraints.
     *
     * The measurement should respect the constraints and return a [Placeable]
     * that can be positioned during layout.
     *
     * @param constraints The constraints to measure within.
     * @return A Placeable with the measured size and content.
     */
    fun measure(constraints: Constraints): Placeable
}

/**
 * A measured component that can be placed at a position.
 */
interface Placeable {
    /**
     * The measured width in characters.
     */
    val width: Int

    /**
     * The measured height in lines.
     */
    val height: Int

    /**
     * The rendered content as lines.
     */
    val lines: List<String>

    /**
     * Position where this placeable was placed (set during layout).
     */
    var x: Int
    var y: Int
}

/**
 * A [Placeable] whose [lines] are composed of whole, ordered segments.
 *
 * This is useful for renderers that want to keep multi-line widgets intact (e.g., avoid splitting a
 * bordered panel across scrollback vs. an in-place "active area").
 *
 * The sum of [segmentHeights] must equal [lines].size.
 */
interface SegmentedPlaceable : Placeable {
    /** Heights (in lines) of each segment in order. */
    val segmentHeights: List<Int>
}

/**
 * Simple implementation of Placeable.
 */
data class SimplePlaceable(
    override val width: Int,
    override val height: Int,
    override val lines: List<String>,
    override var x: Int = 0,
    override var y: Int = 0,
) : Placeable {
    companion object {
        val Empty = SimplePlaceable(0, 0, emptyList())
    }
}

/**
 * Simple implementation of [SegmentedPlaceable].
 */
data class SegmentedSimplePlaceable(
    override val width: Int,
    override val height: Int,
    override val lines: List<String>,
    override val segmentHeights: List<Int>,
    override var x: Int = 0,
    override var y: Int = 0,
) : SegmentedPlaceable

/**
 * Result of measuring a layout.
 */
data class MeasureResult(
    /**
     * The measured width.
     */
    val width: Int,

    /**
     * The measured height.
     */
    val height: Int,

    /**
     * Function to perform layout and placement.
     */
    val placementBlock: PlacementScope.() -> Unit = {}
)

/**
 * Scope for placing children during layout.
 */
interface PlacementScope {
    /**
     * Place a placeable at the given position.
     *
     * @param x X position (column).
     * @param y Y position (row).
     */
    fun Placeable.placeAt(x: Int, y: Int)
}

/**
 * Simple implementation of PlacementScope.
 */
class SimplePlacementScope : PlacementScope {
    private val placements = mutableListOf<Pair<Placeable, Pair<Int, Int>>>()

    override fun Placeable.placeAt(x: Int, y: Int) {
        this.x = x
        this.y = y
        placements.add(this to (x to y))
    }

    fun getPlacements(): List<Pair<Placeable, Pair<Int, Int>>> = placements.toList()
}

/**
 * Policy for measuring and laying out children.
 */
interface MeasurePolicy {
    /**
     * Measure all children and determine the layout size.
     *
     * @param measurables The children to measure.
     * @param constraints The constraints for this layout.
     * @return The measurement result with size and placement function.
     */
    fun measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult
}
