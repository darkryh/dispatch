package com.ead.dispatch.sample.presentation.library

import com.ead.dispatch.sample.presentation.library.model.ListEntry

internal fun matchesQuery(query: String, vararg values: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return true
    return values.any { it.lowercase().contains(normalized) }
}

internal fun <T> filterEntries(
    entries: List<ListEntry<T>>,
    query: String,
    matcher: (T, String) -> Boolean,
): List<ListEntry<T>> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return entries

    val createEntries = entries.filterIsInstance<ListEntry.Create>()
    val itemEntries = entries.filterIsInstance<ListEntry.Item<T>>()
    val filteredItems = itemEntries.filter { matcher(it.data, normalized) }

    return buildList {
        addAll(createEntries)
        addAll(filteredItems)
    }
}

internal fun defaultSelectionIndex(entries: List<ListEntry<*>>, query: String): Int {
    if (entries.isEmpty()) return 0
    if (query.isBlank()) return 0
    val firstIsCreate = entries.first() is ListEntry.Create
    return if (firstIsCreate && entries.size > 1) 1 else 0
}
