package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.ArcListRoute
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

class ArcListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ArcListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<ArcListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<ArcListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getArcsByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Arc", "Create a new narrative arc"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load arcs.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryArcRecord): ArcListItem {
        val title = record.title.ifBlank { "(untitled)" }
        val scope = record.scopeType.name
        val summary = record.summary?.takeIf { it.isNotBlank() } ?: "no summary"
        val status = record.status?.name ?: "DRAFT"
        return ArcListItem(
            id = record.id,
            title = title,
            scope = scope,
            summary = summary,
            status = "status: ${status.lowercase()}",
        )
    }
}

data class ArcListItem(
    val id: String,
    val title: String,
    val scope: String,
    val summary: String,
    val status: String,
)
