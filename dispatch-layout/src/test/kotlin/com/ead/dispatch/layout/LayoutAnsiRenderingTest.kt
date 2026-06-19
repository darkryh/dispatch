package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutAnsiRenderingTest {
    private class StubMeasurable(
        override val modifier: Modifier = Modifier,
        private val placeable: Placeable,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable = placeable
    }

    private class FixedPlacementPolicy(
        private val size: Pair<Int, Int>,
        private val placements: List<Pair<Int, Int>>,
    ) : MeasurePolicy {
        override fun measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            val placeables = measurables.map { it.measure(constraints) }
            val (width, height) = size
            return MeasureResult(width = width, height = height) {
                for ((index, placeable) in placeables.withIndex()) {
                    val (x, y) = placements[index]
                    placeable.placeAt(x, y)
                }
            }
        }
    }

    private fun stripAnsi(text: String): String {
        // CSI (ESC[...<final>), OSC (ESC]...BEL or ESC\), or 2-byte escapes (ESC<ch>)
        val regex = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]|\u001B\].*?(?:\u0007|\u001B\\)|\u001B.""")
        return text.replace(regex, "")
    }

    @Test
    fun `ansi escape sequences do not consume columns`() {
        val redX = "\u001B[31mX\u001B[0m"
        val child =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 1,
                        height = 1,
                        lines = listOf(redX),
                    ),
            )

        val measurable =
            LayoutMeasurable(
                modifier = Modifier,
                measurePolicy =
                    FixedPlacementPolicy(
                        size = 1 to 1,
                        placements = listOf(0 to 0),
                    ),
                children = { listOf(child) },
            )

        val placeable = measurable.measure(Constraints(maxWidth = 1, maxHeight = 1))
        val line = placeable.lines.single()

        assertTrue(line.contains("X"), "expected visible content to be present even with ANSI codes")
        assertEquals("X", stripAnsi(line))
    }

    @Test
    fun `ansi content keeps horizontal alignment`() {
        val redX = "\u001B[31mX\u001B[0m"
        val leftPad =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 1,
                        height = 1,
                        lines = listOf(" "),
                    ),
            )
        val styled =
            StubMeasurable(
                placeable =
                    SimplePlaceable(
                        width = 1,
                        height = 1,
                        lines = listOf(redX),
                    ),
            )

        val measurable =
            LayoutMeasurable(
                modifier = Modifier,
                measurePolicy =
                    FixedPlacementPolicy(
                        size = 2 to 1,
                        placements = listOf(0 to 0, 1 to 0),
                    ),
                children = { listOf(leftPad, styled) },
            )

        val placeable = measurable.measure(Constraints(maxWidth = 2, maxHeight = 1))
        assertEquals(" X", stripAnsi(placeable.lines.single()))
    }
}
