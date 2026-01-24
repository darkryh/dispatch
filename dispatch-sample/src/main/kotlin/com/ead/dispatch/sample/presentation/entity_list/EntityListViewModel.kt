package com.ead.dispatch.sample.presentation.entity_list

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.EntityListRoute
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntityListState(
    val isLoading: Boolean = true,
    val storyId: String? = null,
    val type: String? = null,
    val items: List<EntityPreview> = emptyList(),
    val error: String? = null,
)

data class EntityPreview(
    val id: String,
    val title: String,
    val subtitle: String,
    val isCreate: Boolean = false,
)

class EntityListViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntityListRoute>()

    private val _state = MutableStateFlow(
        EntityListState(
            storyId = route.storyId,
            type = route.type.lowercase(),
        )
    )
    val state: StateFlow<EntityListState> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
            val type = route.type.lowercase()
            if (type !in setOf("characters", "locations", "arcs", "world-rules", "cultures", "events", "organizations", "relationships", "location-features", "artifacts", "timeline", "volumes", "chapters", "scenes")) {
                _state.update {
                    it.copy(
                        isLoading = false,
                            error = "Unknown list type: $type",
                        )
                    }
                    return@launch
                }

                runCatching {
                val items = when (type) {
                    "characters" -> {
                        val existing = repository.getStoryCharacters(storyId).map { character ->
                            val roles = character.roles.takeIf { it.isNotEmpty() }?.joinToString() ?: "no roles"
                            EntityPreview(character.id, character.name.ifBlank { "(unnamed)" }, "roles=$roles")
                        }
                        listOf(
                            EntityPreview(
                                id = "create",
                                title = "+ Create character",
                                subtitle = "Start a new character profile",
                                isCreate = true,
                            )
                        ) + existing
                    }
                    "locations" -> {
                        repository.getLocationsByStory(storyId).map { location ->
                            val name = location.profile.name.ifBlank { "(unnamed)" }
                            val desc = location.profile.description?.takeIf { it.isNotBlank() } ?: "no description"
                            EntityPreview(location.id, name, desc)
                        }
                    }
                    "arcs" -> {
                        repository.getArcsByStory(storyId).map { arc ->
                            val label = arc.title.ifBlank { "(untitled)" }
                            val scope = arc.scopeType.name.lowercase()
                            EntityPreview(arc.id, label, "scope=$scope")
                        }
                    }
                    "world-rules" -> {
                        repository.getWorldRulesByStory(storyId).map { rule ->
                            EntityPreview(rule.id, rule.title, rule.description ?: "")
                        }
                    }
                    "cultures" -> {
                        repository.getCulturesByStory(storyId).map { culture ->
                            EntityPreview(culture.id, culture.name, culture.description ?: "")
                        }
                    }
                    "events" -> {
                        repository.getEventsByStory(storyId).map { event ->
                            EntityPreview(event.id, event.name, event.description ?: "")
                        }
                    }
                    "organizations" -> {
                        repository.getOrganizationsByStory(storyId).map { org ->
                            EntityPreview(org.id, org.name, org.description ?: "")
                        }
                    }
                    "relationships" -> {
                        repository.getRelationshipsByStory(storyId).map { rel ->
                            val subtitle = "${rel.subjectType}:${rel.subjectId} -> ${rel.objectType}:${rel.objectId}"
                            EntityPreview(rel.id, rel.relation, subtitle)
                        }
                    }
                    "location-features" -> {
                        repository.getLocationFeaturesByStory(storyId).map { feature ->
                            val subtitle = feature.locationId?.let { "location=$it" } ?: "no location"
                            EntityPreview(feature.id, feature.name, subtitle)
                        }
                    }
                    "artifacts" -> {
                        repository.getArtifactsByStory(storyId).map { artifact ->
                            val subtitle = artifact.ownerId?.let { "owner=$it" } ?: "no owner"
                            EntityPreview(artifact.id, artifact.name, subtitle)
                        }
                    }
                    "timeline" -> {
                        repository.getTimelineEntriesByStory(storyId).map { entry ->
                            val subtitle = "order=${entry.orderIndex}"
                            EntityPreview(entry.id, entry.title, subtitle)
                        }
                    }
                    "volumes" -> {
                        repository.getVolumesByStory(storyId).map { volume ->
                            val title = volume.title.ifBlank { "(untitled)" }
                            EntityPreview(volume.id, "Volume ${volume.number}", "title=$title")
                        }
                    }
                    "chapters" -> {
                        val volumes = repository.getVolumesByStory(storyId)
                        volumes.flatMap { volume ->
                            repository.getChaptersByVolume(volume.id).map { chapter ->
                                val title = chapter.title.ifBlank { "(untitled)" }
                                EntityPreview(chapter.id, "Ch ${chapter.number}", "vol ${volume.number} · $title")
                            }
                        }
                    }
                    "scenes" -> {
                        val volumes = repository.getVolumesByStory(storyId)
                        val chapters = volumes.flatMap { volume -> repository.getChaptersByVolume(volume.id) }
                        chapters.flatMap { chapter ->
                            repository.getScenesByChapter(chapter.id).map { scene ->
                                val title = scene.title?.ifBlank { "(untitled)" } ?: "(untitled)"
                                EntityPreview(scene.id, "Scene ${scene.number}", "ch ${chapter.number} · $title")
                            }
                        }
                    }
                    else -> emptyList()
                }
                    _state.update { it.copy(isLoading = false, items = items) }
                }.onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load list.")
                    }
                }
            }
        }
    }
}
