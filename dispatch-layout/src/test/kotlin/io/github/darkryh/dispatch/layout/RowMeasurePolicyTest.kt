package io.github.darkryh.dispatch.layout

import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.weight
import kotlin.test.Test
import kotlin.test.assertEquals

class RowMeasurePolicyTest {
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
    fun `fixed children are measured with remaining width`() {
        val seen = mutableListOf<Int>()

        val first =
            CapturingMeasurable(
                onMeasure = { seen += it.maxWidth },
                placeable = SimplePlaceable(width = 2, height = 1, lines = listOf("> ")),
            )
        val second =
            CapturingMeasurable(
                onMeasure = { seen += it.maxWidth },
                placeable = SimplePlaceable(width = 8, height = 1, lines = listOf("hi")),
            )

        val policy =
            RowMeasurePolicy(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top,
            )

        policy.measure(
            measurables = listOf(first, second),
            constraints = Constraints(minWidth = 0, maxWidth = 10, minHeight = 0, maxHeight = Int.MAX_VALUE),
        )

        assertEquals(listOf(10, 8), seen)
    }

    @Test
    fun `weight is ignored when width is unbounded`() {
        var seen: Constraints? = null

        val weighted =
            CapturingMeasurable(
                onMeasure = { seen = it },
                placeable = SimplePlaceable(width = 1, height = 1, lines = listOf("X")),
                modifier = Modifier.weight(1f),
            )

        val policy =
            RowMeasurePolicy(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top,
            )

        policy.measure(
            measurables = listOf(weighted),
            constraints = Constraints(minWidth = 0, maxWidth = Int.MAX_VALUE, minHeight = 0, maxHeight = Int.MAX_VALUE),
        )

        assertEquals(0, seen?.minWidth)
        assertEquals(Int.MAX_VALUE, seen?.maxWidth)
    }

    @Test
    fun `spacedBy reports width including gaps and places children past them`() {
        val children = List(3) { SimplePlaceable(width = 38, height = 1, lines = listOf("x".repeat(38))) }

        val policy =
            RowMeasurePolicy(
                horizontalArrangement = Arrangement.spacedBy(2),
                verticalAlignment = Alignment.Top,
            )

        val result =
            policy.measure(
                measurables = children.map { CapturingMeasurable(onMeasure = {}, placeable = it) },
                constraints = Constraints(minWidth = 0, maxWidth = Int.MAX_VALUE, minHeight = 0, maxHeight = 24),
            )
        result.placementBlock(SimplePlacementScope())

        // 3 children x 38 cells + 2 gaps x 2 cells: the reported width must cover the placed span.
        assertEquals(118, result.width)
        assertEquals(listOf(0, 40, 80), children.map { it.x })
    }

    @Test
    fun `spacedBy gaps are reserved out of the bounded width budget`() {
        val seenMaxWidths = mutableListOf<Int>()

        val measurables =
            List(2) {
                CapturingMeasurable(
                    onMeasure = { seenMaxWidths += it.maxWidth },
                    placeable = SimplePlaceable(width = 3, height = 1, lines = listOf("abc")),
                )
            }

        val policy =
            RowMeasurePolicy(
                horizontalArrangement = Arrangement.spacedBy(2),
                verticalAlignment = Alignment.Top,
            )

        val result =
            policy.measure(
                measurables = measurables,
                constraints = Constraints(minWidth = 0, maxWidth = 10, minHeight = 0, maxHeight = Int.MAX_VALUE),
            )

        // The gap between the two children consumes 2 of the 10 cells up front: the first child
        // may use at most 8, the second whatever the first left over.
        assertEquals(listOf(8, 5), seenMaxWidths)
        assertEquals(8, result.width)
    }
}
