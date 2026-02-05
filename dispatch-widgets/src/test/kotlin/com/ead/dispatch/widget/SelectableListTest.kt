package com.ead.dispatch.widget

import com.github.ajalt.mordant.rendering.TextColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectableListTest {

    @Test
    fun `selected item uses selected prefix`() {
        val lines = renderLines(width = 40) {
            SelectableList(
                items = listOf("Alpha", "Beta"),
                selectedIndex = 1,
                styles = SelectableListStyles(
                    prefix = TextColors.white,
                    selectedPrefix = TextColors.white,
                    prefixText = "--",
                    selectedPrefixText = ">>",
                ),
            ) { item, _ ->
                Text(text = item)
            }
        }

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("--"))
        assertTrue(lines[1].contains(">>"))
    }
}
