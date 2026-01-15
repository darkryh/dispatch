package com.ead.dispatch.sample.presentation.story_info

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.sample.navigation.StoryInfoRoute
import com.ead.dispatch.viewmodel.ViewModel
import com.ead.dispatch.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryInfoState(
    val isLoading: Boolean = true,
    val storyId: String? = null,
    val context: StoryChatContext? = null,
    val error: String? = null,
)

class StoryInfoViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<StoryInfoRoute>()

    private val _state = MutableStateFlow(StoryInfoState(storyId = route.storyId))
    val state: StateFlow<StoryInfoState> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.getChatContext(storyId) }
                    .onSuccess { context ->
                        _state.update { it.copy(isLoading = false, context = context) }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load story info.") }
                    }
            }
        }
    }
}
