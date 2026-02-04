package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryArtifactRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.ArtifactListRoute
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

class ArtifactListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ArtifactListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<ArtifactListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<ArtifactListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getArtifactsByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Artifact", "Catalog a new artifact"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load artifacts.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryArtifactRecord): ArtifactListItem {
        val name = record.name.ifBlank { "(unnamed)" }
        val description = record.description?.takeIf { it.isNotBlank() } ?: "no description"
        val ownerLabel = if (!record.ownerId.isNullOrBlank()) {
            val type = record.ownerType?.ifBlank { "owner" } ?: "owner"
            "$type:${record.ownerId}"
        } else {
            "none"
        }
        val locationLabel = record.locationId?.takeIf { it.isNotBlank() } ?: "none"
        return ArtifactListItem(
            id = record.id,
            name = name,
            owner = "owner: $ownerLabel",
            location = "location: $locationLabel",
            description = description,
        )
    }
}

data class ArtifactListItem(
    val id: String,
    val name: String,
    val owner: String,
    val location: String,
    val description: String,
)
