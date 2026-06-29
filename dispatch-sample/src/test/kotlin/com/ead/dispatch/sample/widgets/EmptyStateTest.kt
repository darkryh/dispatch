package com.ead.dispatch.sample.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmptyStateTest {
    @Test
    fun `renders title and description`() {
        val lines =
            renderLines(width = 50) {
                EmptyState(
                    title = "Nothing here",
                    description = "Try again later",
                    leftPadding = 0,
                )
            }

        assertTrue(lines.size >= 2)
        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("Nothing here"))
        assertTrue(joined.contains("Try again later"))
    }

    @Test
    fun `renders title only when description is null`() {
        val lines =
            renderLines(width = 50) {
                EmptyState(
                    title = "Nothing here",
                    leftPadding = 0,
                )
            }

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("Nothing here"))
    }
}
