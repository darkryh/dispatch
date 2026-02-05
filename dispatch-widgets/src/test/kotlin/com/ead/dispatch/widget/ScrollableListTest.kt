package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollableListTest {

    @Test
    fun `renders all items in unbounded mode`() {
        val lines = renderLines(width = 40) {
            ScrollableList(
                items = listOf("Alpha", "Beta", "Gamma"),
            ) { item ->
                Text(item)
            }
        }

        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("Alpha"))
        assertTrue(lines[1].contains("Beta"))
        assertTrue(lines[2].contains("Gamma"))
    }
}
