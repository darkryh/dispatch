package com.ead.dispatch.sample.presentation.chat_mode.story

import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.ChapterListRoute
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.sample.presentation.library.state.EntityListState
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChapterListItem(
    val id: String,
    val volumeId: String,
    val title: String,
    val summary: String,
    val meta: String,
)

class ChapterListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ChapterListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<ChapterListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<ChapterListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No story id provided.") }
        } else {
            val selectedVolumeId = route.volumeId?.trim().takeIf { !it.isNullOrEmpty() }

            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val storyExists = repository.getStoryById(storyId) != null
                    if (!storyExists) {
                        throw IllegalStateException("Story not found.")
                    }

                    val volumes = repository.getVolumesByStory(storyId).sortedBy { it.number }
                    val targetVolumes = if (selectedVolumeId == null) {
                        volumes
                    } else {
                        volumes.filter { it.id == selectedVolumeId }
                    }

                    targetVolumes.flatMap { volume ->
                        repository.getChaptersByVolume(volume.id)
                            .sortedBy { it.number }
                            .map { chapter -> ListEntry.Item(toListItem(volume, chapter)) }
                    }
                }.onSuccess { entries ->
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load chapters.") }
                }
            }
        }
    }

    private fun toListItem(
        volume: StoryVolumeRecord,
        chapter: StoryChapterRecord,
    ): ChapterListItem {
        val status = chapter.status?.name ?: "DRAFT"
        val summary = chapter.summary?.takeIf { it.isNotBlank() } ?: "no summary"
        val wordCount = chapter.content?.wordCount
        val words = if (wordCount != null) "$wordCount words" else "word count unknown"
        return ChapterListItem(
            id = chapter.id,
            volumeId = volume.id,
            title = "Vol ${volume.number} · Ch ${chapter.number}: ${chapter.title}",
            summary = summary,
            meta = "$status · $words",
        )
    }
}
