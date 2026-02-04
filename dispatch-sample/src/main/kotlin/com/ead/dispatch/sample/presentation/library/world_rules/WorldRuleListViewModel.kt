package com.ead.dispatch.sample.presentation.library.world_rules

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryWorldRuleRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.WorldRuleListRoute
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

class WorldRuleListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<WorldRuleListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<WorldRuleListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<WorldRuleListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getWorldRulesByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New World Rule", "Create a new constraint"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load world rules.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryWorldRuleRecord): WorldRuleListItem {
        val title = record.title.ifBlank { "(untitled)" }
        val description = record.description?.takeIf { it.isNotBlank() } ?: "no description"
        return WorldRuleListItem(
            id = record.id,
            title = title,
            description = description,
        )
    }
}

data class WorldRuleListItem(
    val id: String,
    val title: String,
    val description: String,
)
