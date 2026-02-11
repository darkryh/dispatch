package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.weight
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
}
