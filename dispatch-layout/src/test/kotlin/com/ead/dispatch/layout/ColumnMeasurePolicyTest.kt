package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.weight
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
}
