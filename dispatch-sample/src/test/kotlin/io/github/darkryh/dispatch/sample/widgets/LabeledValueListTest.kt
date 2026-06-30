package io.github.darkryh.dispatch.sample.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabeledValueListTest {
    @Test
    fun `skips blank values and renders labels`() {
        val lines =
            renderLines(width = 60) {
                LabeledValueList(
                    items =
                        listOf(
                            LabeledValue(label = "Name", value = "Nexus"),
                            LabeledValue(label = "Empty", value = "  "),
                        ),
                    leftPadding = 0,
                )
            }

        assertEquals(1, lines.size)
        assertTrue(lines.first().contains("Name:"))
        assertTrue(lines.first().contains("Nexus"))
    }
}
