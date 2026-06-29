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
 * A placeable with an explicit boundary between native-scrolling content and the active viewport.
 * Lines before [activeStartLine] may enter terminal scrollback; lines at and after it are updated
 * in place. Layout containers preserve this boundary when wrapping a terminal screen.
 */
interface RenderRegionPlaceable : Placeable {
    val scrollingStartLine: Int
    val activeStartLine: Int
}

/**
 * Simple implementation of Placeable.
 */
@Suppress("DataClassShouldBeImmutable")
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
@Suppress("DataClassShouldBeImmutable")
data class SegmentedSimplePlaceable(
    override val width: Int,
    override val height: Int,
    override val lines: List<String>,
    override val segmentHeights: List<Int>,
    override var x: Int = 0,
    override var y: Int = 0,
) : SegmentedPlaceable

@Suppress("DataClassShouldBeImmutable")
data class RenderRegionSimplePlaceable(
    override val width: Int,
    override val height: Int,
    override val lines: List<String>,
    override val scrollingStartLine: Int = 0,
    override val activeStartLine: Int,
    override var x: Int = 0,
    override var y: Int = 0,
) : RenderRegionPlaceable

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

    /** Explicit active-region boundary for terminal-screen layouts. */
    val activeStartLine: Int? = null,

    /** First line of flowing content, after any mutable screen header. */
    val scrollingStartLine: Int? = null,

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
    private val placements = mutableListOf<Placeable>()

    override fun Placeable.placeAt(x: Int, y: Int) {
        this.x = x
        this.y = y
        placements.add(this)
    }

    // placeAt already records the position on each Placeable's x/y, so there is no need to also
    // store nested Pair<Placeable, Pair<Int, Int>> tuples (two boxed ints + two Pairs per child).
    // Consumers read placeable.x / placeable.y directly.
    fun getPlacements(): List<Placeable> = placements.toList()
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
