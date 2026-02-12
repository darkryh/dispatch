package com.ead.dispatch.sample.presentation.chat_mode.story

import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.StoryChatRoute
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryCountItem(
    val label: String,
    val count: Int,
)

data class StoryChatState(
    val isLoading: Boolean = true,
    val storyId: String? = null,
    val story: StoryRecord? = null,
    val counts: List<StoryCountItem> = emptyList(),
    val error: String? = null,
)

class StoryChatViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<StoryChatRoute>()
    private val _state = MutableStateFlow(StoryChatState(storyId = route.storyId))
    val state: StateFlow<StoryChatState> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val story = repository.getStoryById(storyId)
                    if (story == null) {
                        _state.update { it.copy(isLoading = false, error = "No story found for this session.") }
                        return@runCatching
                    }

                    val characters = repository.getStoryCharacters(storyId)
                    val locations = repository.getLocationsByStory(storyId)
                    val arcs = repository.getArcsByStory(storyId)
                    val worldRules = repository.getWorldRulesByStory(storyId)
                    val cultures = repository.getCulturesByStory(storyId)
                    val events = repository.getEventsByStory(storyId)
                    val organizations = repository.getOrganizationsByStory(storyId)
                    val relationships = repository.getRelationshipsByStory(storyId)
                    val locationFeatures = repository.getLocationFeaturesByStory(storyId)
                    val artifacts = repository.getArtifactsByStory(storyId)
                    val timelineEntries = repository.getTimelineEntriesByStory(storyId)
                    val volumes = repository.getVolumesByStory(storyId)
                    val chapters = volumes.flatMap { volume -> repository.getChaptersByVolume(volume.id) }
                    val scenes = chapters.flatMap { chapter -> repository.getScenesByChapter(chapter.id) }

                    val counts = listOf(
                        StoryCountItem("Characters", characters.size),
                        StoryCountItem("Locations", locations.size),
                        StoryCountItem("Arcs", arcs.size),
                        StoryCountItem("World Rules", worldRules.size),
                        StoryCountItem("Cultures", cultures.size),
                        StoryCountItem("Events", events.size),
                        StoryCountItem("Organizations", organizations.size),
                        StoryCountItem("Relationships", relationships.size),
                        StoryCountItem("Artifacts", artifacts.size),
                        StoryCountItem("Location Features", locationFeatures.size),
                        StoryCountItem("Timeline Entries", timelineEntries.size),
                        StoryCountItem("Volumes", volumes.size),
                        StoryCountItem("Chapters", chapters.size),
                        StoryCountItem("Scenes", scenes.size),
                    )

                    _state.update {
                        it.copy(
                            isLoading = false,
                            story = story,
                            counts = counts,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load story data.",
                        )
                    }
                }
            }
        }
    }

}
