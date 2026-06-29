package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentedButtonTest {
    @Test
    fun `nextOption returns first when current missing`() {
        val options = listOf("STORY", "VOLUME", "CHAPTER")
        assertEquals("STORY", nextOption("", options))
        assertEquals("STORY", nextOption("UNKNOWN", options))
    }

    @Test
    fun `nextOption advances through list`() {
        val options = listOf("DRAFT", "IN_PROGRESS", "FINAL")
        assertEquals("IN_PROGRESS", nextOption("DRAFT", options))
        assertEquals("FINAL", nextOption("IN_PROGRESS", options))
    }

    @Test
    fun `nextOption wraps to first`() {
        val options = listOf("A", "B", "C")
        assertEquals("A", nextOption("C", options))
    }

    @Test
    fun `nextOption returns null when options empty`() {
        assertNull(nextOption("ANY", emptyList()))
    }

    @Test
    fun `formatOptionLabel title cases enum values`() {
        assertEquals("Story", formatOptionLabel("STORY"))
        assertEquals("In Progress", formatOptionLabel("IN_PROGRESS"))
    }

    @Test
    fun `formatOptionLabel keeps non uppercase`() {
        assertEquals("Scene", formatOptionLabel("Scene"))
        assertEquals("in_progress", formatOptionLabel("in_progress"))
    }
}
