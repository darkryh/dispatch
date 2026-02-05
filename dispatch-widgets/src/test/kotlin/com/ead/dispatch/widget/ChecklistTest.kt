package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChecklistTest {

    @Test
    fun `renders checked and unchecked indicators`() {
        val lines = renderLines(width = 40) {
            Checklist(
                items = listOf(
                    ChecklistItem(name = "One", checked = true),
                    ChecklistItem(name = "Two", checked = false),
                ),
                selectedIndex = 0,
            )
        }

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("[✓]"))
        assertTrue(lines[1].contains("[ ]"))
        assertTrue(lines[0].trimStart().startsWith(">"))
    }
}
