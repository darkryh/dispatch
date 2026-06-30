package io.github.darkryh.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChipRowTest {
    @Test
    fun `tag list renders tags`() {
        val lines =
            renderLines(width = 40) {
                ChipRow(
                    tags = listOf("alpha", "beta"),
                    gap = 1,
                )
            }

        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("alpha"))
        assertTrue(joined.contains("beta"))
    }

    @Test
    fun `tag pill renders text`() {
        val lines =
            renderLines(width = 8) {
                Chip(
                    text = "tag",
                    paddingHorizontal = 2,
                )
            }

        assertEquals(1, lines.size)
        assertTrue(lines.first().contains("tag"))
    }
}
