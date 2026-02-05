package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionHeaderTest {

    @Test
    fun `subtitle on new line renders two lines`() {
        val lines = renderLines(width = 40) {
            SectionHeader(
                title = "Profile",
                subtitle = "Hint goes here",
                leftPadding = 0,
                subtitleOnNewLine = true,
            )
        }

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("Profile"))
        assertTrue(lines[1].contains("Hint goes here"))
    }

    @Test
    fun `inline subtitle renders single line`() {
        val lines = renderLines(width = 40) {
            SectionHeader(
                title = "Profile",
                subtitle = "Hint goes here",
                leftPadding = 0,
                subtitleOnNewLine = false,
            )
        }

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("Profile"))
        assertTrue(lines[0].contains("Hint goes here"))
    }
}
