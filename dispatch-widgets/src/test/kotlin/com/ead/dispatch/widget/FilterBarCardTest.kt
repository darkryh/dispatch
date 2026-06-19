package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertTrue

class FilterBarCardTest {
    @Test
    fun `filter bar card renders placeholder`() {
        val state = TextFieldState("")
        val lines =
            renderLines(width = 40) {
                FilterBarCard(
                    state = state,
                    placeholder = "Type to filter...",
                )
            }

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("Type to filter") })
    }
}
