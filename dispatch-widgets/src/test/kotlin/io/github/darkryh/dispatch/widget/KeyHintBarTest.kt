package io.github.darkryh.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyHintBarTest {
    @Test
    fun `renders key hints with separator`() {
        val lines =
            renderLines(width = 60) {
                KeyHintBar(
                    hints =
                        listOf(
                            KeyHint("Esc", "back"),
                            KeyHint("Enter", "open"),
                        ),
                    leftPadding = 0,
                    separator = "|",
                )
            }

        assertEquals(1, lines.size)
        val line = lines.first()
        assertTrue(line.contains("Esc"))
        assertTrue(line.contains("back"))
        assertTrue(line.contains("Enter"))
        assertTrue(line.contains("open"))
        assertTrue(line.contains("|"))
    }

    @Test
    fun `empty hints render no output`() {
        val lines =
            renderLines(width = 40) {
                KeyHintBar(hints = emptyList())
            }

        assertTrue(lines.isEmpty())
    }
}
