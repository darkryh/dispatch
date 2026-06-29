package com.ead.dispatch.sample.widgets

import com.ead.dispatch.widget.GridCells
import kotlin.test.Test
import kotlin.test.assertTrue

class CountTileGridTest {
    @Test
    fun `truncates long labels in tiles`() {
        val lines =
            renderLines(width = 40) {
                CountTileGrid(
                    items =
                        listOf(
                            CountTile(label = "VeryLongLocationNameThatShouldTruncate", count = 12),
                            CountTile(label = "Short", count = 3),
                        ),
                    cells = GridCells.Fixed(2),
                    gap = 2,
                    leftPadding = 0,
                )
            }

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("...") })
        assertTrue(lines.any { it.contains("12") })
        assertTrue(lines.any { it.contains("3") })
    }
}
