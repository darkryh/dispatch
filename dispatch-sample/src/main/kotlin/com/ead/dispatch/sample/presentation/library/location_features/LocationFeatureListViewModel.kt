package com.ead.dispatch.sample.presentation.library.location_features

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryLocationFeatureRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.LocationFeatureListRoute
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

class LocationFeatureListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<LocationFeatureListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<LocationFeatureListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<LocationFeatureListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getLocationFeaturesByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Feature", "Add a location feature"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load features.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryLocationFeatureRecord): LocationFeatureListItem {
        val name = record.name.ifBlank { "(unnamed)" }
        val description = record.description?.takeIf { it.isNotBlank() } ?: "no description"
        val location = record.locationId?.takeIf { it.isNotBlank() } ?: "none"
        return LocationFeatureListItem(
            id = record.id,
            name = name,
            location = "location: $location",
            description = description,
        )
    }
}

data class LocationFeatureListItem(
    val id: String,
    val name: String,
    val location: String,
    val description: String,
)
