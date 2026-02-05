package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridLayoutTest {

    @Test
    fun `buildGridRows returns rows and cell width`() {
        val items = listOf("A", "B", "C", "D")
        val result = buildGridRows(items, cells = GridCells.Adaptive(minSize = 10), gap = 2, leftPadding = 2)
        assertTrue(result.rows.isNotEmpty())
        assertTrue(result.cellWidth >= 1)
    }

    @Test
    fun `buildGridRows clamps perRow to at least one`() {
        val items = listOf("A", "B", "C")
        val result = buildGridRows(items, cells = GridCells.Adaptive(minSize = 2000), gap = 2, leftPadding = 2)
        assertEquals(3, result.rows.flatten().size)
        assertEquals(1, result.rows.first().size)
    }

    @Test
    fun `buildGridRows honors fixed column count`() {
        val items = listOf("A", "B", "C", "D", "E")
        val result = buildGridRows(items, cells = GridCells.Fixed(2), gap = 2, leftPadding = 2)
        assertEquals(2, result.rows.first().size)
    }
}
