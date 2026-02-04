package com.ead.dispatch.sample.presentation.library.timeline

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryTimelineEntryRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.TimelineListRoute
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.sample.presentation.library.state.EntityListState
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimelineListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TimelineListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<TimelineListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<TimelineListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getTimelineEntriesByStory(storyId)
                        .sortedBy { it.orderIndex }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Timeline Entry", "Add a timeline beat"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load timeline.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryTimelineEntryRecord): TimelineListItem {
        val title = record.title.ifBlank { "(untitled)" }
        val description = record.description?.takeIf { it.isNotBlank() } ?: "no description"
        return TimelineListItem(
            id = record.id,
            order = record.orderIndex,
            title = title,
            description = description,
        )
    }
}

data class TimelineListItem(
    val id: String,
    val order: Long,
    val title: String,
    val description: String,
)
