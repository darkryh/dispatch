package com.ead.dispatch.sample.presentation.chat_mode.story

import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.VolumeListRoute
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.sample.presentation.library.state.EntityListState
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VolumeListItem(
    val id: String,
    val title: String,
    val summary: String,
    val meta: String,
)

class VolumeListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<VolumeListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<VolumeListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<VolumeListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No story id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val storyExists = repository.getStoryById(storyId) != null
                    if (!storyExists) {
                        throw IllegalStateException("Story not found.")
                    }

                    val volumes = repository.getVolumesByStory(storyId).sortedBy { it.number }
                    volumes.map { volume ->
                        val chapterCount = repository.getChaptersByVolume(volume.id).size
                        ListEntry.Item(toListItem(volume, chapterCount))
                    }
                }.onSuccess { entries ->
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load volumes.") }
                }
            }
        }
    }

    private fun toListItem(
        record: StoryVolumeRecord,
        chapterCount: Int,
    ): VolumeListItem {
        val status = record.plan?.status?.name ?: "DRAFT"
        val summary = record.plan?.summary?.takeIf { it.isNotBlank() } ?: "no summary"
        return VolumeListItem(
            id = record.id,
            title = "Vol ${record.number}: ${record.title}",
            summary = summary,
            meta = "$status · $chapterCount chapter(s)",
        )
    }
}
