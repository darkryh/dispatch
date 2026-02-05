package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyColumnTest {

    @Test
    fun `renders items in order`() {
        val lines = renderLines(width = 40) {
            LazyColumn {
                item { Text("First") }
                item { Text("Second") }
            }
        }

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("First"))
        assertTrue(lines[1].contains("Second"))
    }
}
