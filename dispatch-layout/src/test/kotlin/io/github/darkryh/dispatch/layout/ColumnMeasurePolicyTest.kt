package io.github.darkryh.dispatch.layout

import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.weight
import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnMeasurePolicyTest {
    private class CapturingMeasurable(
        private val onMeasure: (Constraints) -> Unit,
        private val placeable: Placeable,
        override val modifier: Modifier = Modifier,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable {
            onMeasure(constraints)
            return placeable
        }
    }

    @Test
    fun `weight is ignored when height is unbounded`() {
        var seen: Constraints? = null

        val weighted =
            CapturingMeasurable(
                onMeasure = { seen = it },
                placeable = SimplePlaceable(width = 1, height = 1, lines = listOf("X")),
                modifier = Modifier.weight(1f),
            )

        val policy =
            ColumnMeasurePolicy(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            )

        policy.measure(
            measurables = listOf(weighted),
            constraints = Constraints(minWidth = 0, maxWidth = 10, minHeight = 0, maxHeight = Int.MAX_VALUE),
        )

        assertEquals(0, seen?.minHeight)
        assertEquals(Int.MAX_VALUE, seen?.maxHeight)
    }

    @Test
    fun `spacedBy reports height including gaps and places children past them`() {
        val first = SimplePlaceable(width = 4, height = 1, lines = listOf("AAAA"))
        val second = SimplePlaceable(width = 4, height = 1, lines = listOf("BBBB"))

        val policy =
            ColumnMeasurePolicy(
                verticalArrangement = Arrangement.spacedBy(1),
                horizontalAlignment = Alignment.Start,
            )

        val result =
            policy.measure(
                measurables =
                    listOf(
                        CapturingMeasurable(onMeasure = {}, placeable = first),
                        CapturingMeasurable(onMeasure = {}, placeable = second),
                    ),
                constraints = Constraints(minWidth = 0, maxWidth = 80, minHeight = 0, maxHeight = Int.MAX_VALUE),
            )
        result.placementBlock(SimplePlacementScope())

        // 2 children x 1 row + 1 gap: the reported height must cover the placed span.
        assertEquals(3, result.height)
        assertEquals(0, first.y)
        assertEquals(2, second.y)
    }

    @Test
    fun `spacedBy gaps are reserved before weighting children in a bounded column`() {
        var weightedHeight: Constraints? = null

        val fixed =
            CapturingMeasurable(
                onMeasure = {},
                placeable = SimplePlaceable(width = 4, height = 4, lines = List(4) { "FIXD" }),
            )
        val weighted =
            CapturingMeasurable(
                onMeasure = { weightedHeight = it },
                placeable = SimplePlaceable(width = 4, height = 5, lines = List(5) { "WGHT" }),
                modifier = Modifier.weight(1f),
            )

        val policy =
            ColumnMeasurePolicy(
                verticalArrangement = Arrangement.spacedBy(1),
                horizontalAlignment = Alignment.Start,
            )

        val result =
            policy.measure(
                measurables = listOf(fixed, weighted),
                constraints = Constraints(minWidth = 0, maxWidth = 80, minHeight = 0, maxHeight = 10),
            )

        // 10 rows total - 4 fixed - 1 gap = 5 for the weighted child; without the gap
        // reservation it would get 6 and the column would overflow its bounds.
        assertEquals(5, weightedHeight?.maxHeight)
        assertEquals(10, result.height)
    }
}
