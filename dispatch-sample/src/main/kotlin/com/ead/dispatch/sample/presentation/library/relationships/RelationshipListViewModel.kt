package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryRelationshipRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.RelationshipListRoute
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

class RelationshipListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<RelationshipListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<RelationshipListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<RelationshipListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getRelationshipsByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Relationship", "Map a relationship"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load relationships.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryRelationshipRecord): RelationshipListItem {
        val subject = "${record.subjectType}:${record.subjectId}"
        val obj = "${record.objectType}:${record.objectId}"
        val relation = record.relation.ifBlank { "unspecified" }
        val notes = record.notes?.takeIf { it.isNotBlank() } ?: "no notes"
        return RelationshipListItem(
            id = record.id,
            link = "$subject → $obj",
            relation = "relation: $relation",
            notes = "notes: $notes",
        )
    }
}

data class RelationshipListItem(
    val id: String,
    val link: String,
    val relation: String,
    val notes: String,
)
