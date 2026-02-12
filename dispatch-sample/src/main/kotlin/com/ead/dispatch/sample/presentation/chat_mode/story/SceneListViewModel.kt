package com.ead.dispatch.sample.presentation.chat_mode.story

import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.SceneListRoute
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.sample.presentation.library.state.EntityListState
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SceneListItem(
    val id: String,
    val chapterId: String,
    val title: String,
    val summary: String,
    val meta: String,
)

class SceneListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SceneListRoute>()

    private val _state = MutableStateFlow(
        EntityListState<SceneListItem>(
            storyId = route.storyId,
            isLoading = true,
        )
    )
    val state: StateFlow<EntityListState<SceneListItem>> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No story id provided.") }
        } else {
            val selectedChapterId = route.chapterId?.trim().takeIf { !it.isNullOrEmpty() }

            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val storyExists = repository.getStoryById(storyId) != null
                    if (!storyExists) {
                        throw IllegalStateException("Story not found.")
                    }

                    val volumes = repository.getVolumesByStory(storyId).sortedBy { it.number }
                    val chapters = volumes.flatMap { volume ->
                        repository.getChaptersByVolume(volume.id)
                            .sortedBy { it.number }
                            .map { chapter -> volume to chapter }
                    }
                    val targetChapters = if (selectedChapterId == null) {
                        chapters
                    } else {
                        chapters.filter { (_, chapter) -> chapter.id == selectedChapterId }
                    }

                    targetChapters.flatMap { (volume, chapter) ->
                        repository.getScenesByChapter(chapter.id)
                            .sortedBy { it.number }
                            .map { scene -> ListEntry.Item(toListItem(volume, chapter, scene)) }
                    }
                }.onSuccess { entries ->
                    _state.update { it.copy(isLoading = false, items = entries) }
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load scenes.") }
                }
            }
        }
    }

    private fun toListItem(
        volume: StoryVolumeRecord,
        chapter: StoryChapterRecord,
        scene: StorySceneRecord,
    ): SceneListItem {
        val sceneTitle = scene.title?.takeIf { it.isNotBlank() } ?: "(untitled scene)"
        val summary = scene.summary?.takeIf { it.isNotBlank() } ?: "no summary"
        val status = scene.status?.name ?: "DRAFT"
        val pov = scene.context?.pov?.takeIf { it.isNotBlank() } ?: "POV ?"
        return SceneListItem(
            id = scene.id,
            chapterId = chapter.id,
            title = "Vol ${volume.number} · Ch ${chapter.number} · Sc ${scene.number}: $sceneTitle",
            summary = summary,
            meta = "$status · $pov",
        )
    }
}
