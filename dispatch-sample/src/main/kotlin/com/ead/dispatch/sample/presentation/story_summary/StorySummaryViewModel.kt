package com.ead.dispatch.sample.presentation.story_summary

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.StorySummaryRoute
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryCounts(
    val characters: Int = 0,
    val locations: Int = 0,
    val arcs: Int = 0,
    val worldRules: Int = 0,
    val cultures: Int = 0,
    val events: Int = 0,
    val organizations: Int = 0,
    val relationships: Int = 0,
    val locationFeatures: Int = 0,
    val artifacts: Int = 0,
    val timelineEntries: Int = 0,
    val volumes: Int = 0,
    val chapters: Int = 0,
    val scenes: Int = 0,
)

data class StorySummaryState(
    val isLoading: Boolean = true,
    val storyId: String? = null,
    val story: StoryRecord? = null,
    val counts: StoryCounts? = null,
    val error: String? = null,
)

class StorySummaryViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<StorySummaryRoute>()

    private val _state = MutableStateFlow(StorySummaryState(storyId = route.storyId))
    val state: StateFlow<StorySummaryState> = _state.asStateFlow()

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
                        return@launch
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

                    _state.update {
                        it.copy(
                            isLoading = false,
                            story = story,
                            counts = StoryCounts(
                            characters = characters.size,
                            locations = locations.size,
                            arcs = arcs.size,
                            worldRules = worldRules.size,
                            cultures = cultures.size,
                            events = events.size,
                            organizations = organizations.size,
                            relationships = relationships.size,
                            locationFeatures = locationFeatures.size,
                            artifacts = artifacts.size,
                            timelineEntries = timelineEntries.size,
                            volumes = volumes.size,
                            chapters = chapters.size,
                            scenes = scenes.size,
                            ),
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load story summary.",
                        )
                    }
                }
            }
        }
    }
}
