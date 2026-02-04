package com.ead.dispatch.sample.presentation.library.state

import com.ead.dispatch.sample.presentation.library.model.ListEntry

data class EntityListState<T>(
    val storyId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<ListEntry<T>> = emptyList(),
)
