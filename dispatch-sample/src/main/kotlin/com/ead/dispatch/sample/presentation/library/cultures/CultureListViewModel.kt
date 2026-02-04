package com.ead.dispatch.sample.presentation.library.cultures

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryCultureRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.CultureListRoute
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

class CultureListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CultureListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<CultureListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<CultureListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getCulturesByStory(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Culture", "Define a new culture"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load cultures.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryCultureRecord): CultureListItem {
        val name = record.name.ifBlank { "(unnamed)" }
        val description = record.description?.takeIf { it.isNotBlank() } ?: "no description"
        return CultureListItem(
            id = record.id,
            name = name,
            description = description,
        )
    }
}

data class CultureListItem(
    val id: String,
    val name: String,
    val description: String,
)
