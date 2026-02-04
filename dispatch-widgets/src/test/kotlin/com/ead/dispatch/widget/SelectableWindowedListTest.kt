package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectableWindowedListTest {

    @Test
    fun `window slice clamps selected index and count`() {
        val items = (0 until 10).toList()

        val sliceStart = computeWindowSlice(items, selectedIndex = -2, visibleCount = 4)
        assertEquals(0, sliceStart.startIndex)
        assertEquals(4, sliceStart.endIndex)
        assertEquals(0, sliceStart.localSelected)

        val sliceEnd = computeWindowSlice(items, selectedIndex = 99, visibleCount = 4)
        assertEquals(6, sliceEnd.startIndex)
        assertEquals(10, sliceEnd.endIndex)
        assertEquals(3, sliceEnd.localSelected)
    }

    @Test
    fun `window slice keeps selected at end of window`() {
        val items = (0 until 10).toList()

        val slice0 = computeWindowSlice(items, selectedIndex = 0, visibleCount = 4)
        assertEquals(listOf(0, 1, 2, 3), slice0.window)
        assertEquals(0, slice0.localSelected)

        val slice3 = computeWindowSlice(items, selectedIndex = 3, visibleCount = 4)
        assertEquals(listOf(0, 1, 2, 3), slice3.window)
        assertEquals(3, slice3.localSelected)

        val slice4 = computeWindowSlice(items, selectedIndex = 4, visibleCount = 4)
        assertEquals(listOf(1, 2, 3, 4), slice4.window)
        assertEquals(3, slice4.localSelected)
    }

    @Test
    fun `window slice returns full list when count exceeds size`() {
        val items = listOf("a", "b", "c")

        val slice = computeWindowSlice(items, selectedIndex = 1, visibleCount = 10)
        assertEquals(items, slice.window)
        assertEquals(1, slice.localSelected)
    }

    @Test
    fun `window slice handles empty list`() {
        val slice = computeWindowSlice(emptyList<Int>(), selectedIndex = 0, visibleCount = 5)
        assertEquals(0, slice.window.size)
        assertEquals(0, slice.startIndex)
        assertEquals(0, slice.endIndex)
        assertEquals(0, slice.localSelected)
    }
}
