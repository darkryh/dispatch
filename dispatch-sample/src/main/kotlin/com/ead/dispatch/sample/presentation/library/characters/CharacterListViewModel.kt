package com.ead.dispatch.sample.presentation.library.characters

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.CharacterListRoute
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

class CharacterListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CharacterListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<CharacterListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<CharacterListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    repository.getStoryCharacters(storyId)
                        .sortedBy { it.createdAt }
                        .map { toListItem(it) }
                }.onSuccess { items ->
                    val entries = buildList {
                        add(ListEntry.Create("+ New Character", "Create a new character profile"))
                        addAll(items.map { ListEntry.Item(it) })
                    }
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load characters.") }
                }
            }
        }
    }

    private fun toListItem(record: StoryCharacterRecord): CharacterListItem {
        val name = record.name.ifBlank { "(unnamed)" }
        val roles = record.roles.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"
        val age = record.age?.takeIf { it.isNotBlank() }
        val occupation = record.occupation?.takeIf { it.isNotBlank() }
        val goal = record.goal?.takeIf { it.isNotBlank() }
        val temperament = record.temperament?.takeIf { it.isNotBlank() }

        val meta = when {
            age != null || occupation != null -> {
                val ageLabel = age ?: "?"
                val occLabel = occupation ?: "?"
                "age: $ageLabel · occupation: $occLabel"
            }
            goal != null -> "goal: $goal"
            temperament != null -> "temperament: $temperament"
            else -> "notes: -"
        }

        return CharacterListItem(
            id = record.id,
            name = name,
            roles = "roles: $roles",
            meta = meta,
        )
    }
}

data class CharacterListItem(
    val id: String,
    val name: String,
    val roles: String,
    val meta: String,
)
