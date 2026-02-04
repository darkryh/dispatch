package com.ead.dispatch.sample.presentation.library

import com.ead.dispatch.sample.presentation.library.model.ListEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListFilteringTest {

    @Test
    fun `matchesQuery is case-insensitive across fields`() {
        val result = matchesQuery("Echo", "alpha", "echo district", "beta")
        assertTrue(result)
    }

    @Test
    fun `filterEntries keeps create entry and filters items`() {
        val entries = listOf(
            ListEntry.Create("+ New"),
            ListEntry.Item("Alpha"),
            ListEntry.Item("Beta"),
        )

        val filtered = filterEntries(entries, "al") { item, query ->
            matchesQuery(query, item)
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.first() is ListEntry.Create)
        val items = filtered.filterIsInstance<ListEntry.Item<String>>()
        assertEquals(listOf("Alpha"), items.map { it.data })
    }

    @Test
    fun `filterEntries returns original list when query is blank`() {
        val entries = listOf(
            ListEntry.Create("+ New"),
            ListEntry.Item("Alpha"),
        )

        val filtered = filterEntries(entries, "  ") { item, query ->
            matchesQuery(query, item)
        }

        assertEquals(entries, filtered)
    }

    @Test
    fun `defaultSelectionIndex skips create entry when filtering`() {
        val entries = listOf(
            ListEntry.Create("+ New"),
            ListEntry.Item("Alpha"),
        )

        val index = defaultSelectionIndex(entries, "al")

        assertEquals(1, index)
    }
}
