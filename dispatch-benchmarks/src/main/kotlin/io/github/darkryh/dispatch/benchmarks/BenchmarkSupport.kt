package io.github.darkryh.dispatch.benchmarks

import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.Placeable
import io.github.darkryh.dispatch.layout.PlacementScope
import io.github.darkryh.dispatch.layout.SimplePlaceable
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.weight

/**
 * Shared, allocation-light fixtures for the Dispatch microbenchmarks.
 *
 * Everything here is PUBLIC and constructed without reflection. The only `internal` symbols the
 * benchmarks touch (`ScrollingContentTracker`, `ColumnMeasurePolicy`, `RowMeasurePolicy`) are
 * reached via reflection inside each benchmark's `@Setup`, never in the measured loop.
 */

/**
 * A trivial [Measurable] that ignores its constraints and returns a fixed-size [SimplePlaceable].
 *
 * Used to drive the measure policies (Column/Row) and to give [io.github.darkryh.dispatch.layout.LayoutNode]
 * a delegate that carries a real modifier chain (for the focus walk). `lines` is intentionally empty:
 * the measure policies only read `width`/`height`, and the benchmarks never render the placeable.
 */
class FixedMeasurable(
    private val width: Int,
    private val height: Int,
    override val modifier: Modifier = Modifier,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable =
        SimplePlaceable(width = width, height = height, lines = emptyList())
}

/**
 * A [PlacementScope] that performs the real placement side effect (writes x/y) but stores nothing in
 * a list, so running `MeasureResult.placementBlock` allocates ~0. The running [sink] keeps the
 * placement loop from being eliminated as dead code and is fed to a `Blackhole` by callers.
 */
class NoOpPlacementScope : PlacementScope {
    var sink: Long = 0L

    override fun Placeable.placeAt(x: Int, y: Int) {
        this.x = x
        this.y = y
        sink += (x + y).toLong()
    }
}

/** Weighting distribution for the measure benchmarks. */
enum class Weighting { NONE, ALL, HALF }

/**
 * Build [count] [FixedMeasurable]s, attaching a `Modifier.weight(1f)` to children according to
 * [weighting]. Weighted children force the policy down its weight-resolution + second-measure path.
 */
fun buildMeasurables(
    count: Int,
    weighting: Weighting,
    childWidth: Int,
    childHeight: Int,
): List<Measurable> {
    val plain: Modifier = Modifier
    val weighted: Modifier = Modifier.weight(1f)
    return List(count) { index ->
        val modifier =
            when (weighting) {
                Weighting.ALL -> weighted
                Weighting.HALF -> if (index % 2 == 0) weighted else plain
                Weighting.NONE -> plain
            }
        FixedMeasurable(childWidth, childHeight, modifier)
    }
}
