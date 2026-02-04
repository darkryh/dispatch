package com.ead.dispatch.sample.presentation.library.events

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryEventRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.EventListRoute
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

class EventListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EventListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<EventListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<EventListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getEventsByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Event", "Capture a new event"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load events.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryEventRecord): EventListItem {
        val name = record.name.ifBlank { "(unnamed)" }
        val description = record.description?.takeIf { it.isNotBlank() } ?: "no description"
        return EventListItem(
            id = record.id,
            name = name,
            description = description,
        )
    }
}

data class EventListItem(
    val id: String,
    val name: String,
    val description: String,
)
