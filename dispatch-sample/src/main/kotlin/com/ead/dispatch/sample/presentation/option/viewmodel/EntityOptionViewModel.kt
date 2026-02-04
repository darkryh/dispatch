package com.ead.dispatch.sample.presentation.option.viewmodel

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.navigation.EntityOptionRoute
import com.ead.dispatch.sample.presentation.option.model.EntityOptionState
import com.ead.dispatch.sample.presentation.option.model.EntityPreview
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EntityOptionViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntityOptionRoute>()

    private val _state = MutableStateFlow(
        EntityOptionState(
            storyId = route.storyId,
            type = EntityOptionType.fromId(route.type),
        )
    )
    val state: StateFlow<EntityOptionState> = _state.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _state.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val type = EntityOptionType.fromId(route.type)
                if (type == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Unknown list type: ${route.type}",
                        )
                    }
                    return@launch
                }

                runCatching {
                    val items = when (type) {
                        EntityOptionType.CHARACTERS -> {
                            repository.getStoryCharacters(storyId).map { character ->
                                val roles = character.roles.takeIf { it.isNotEmpty() }?.joinToString() ?: "no roles"
                                EntityPreview(character.id, character.name.ifBlank { "(unnamed)" }, "roles=$roles")
                            }
                        }
                        EntityOptionType.LOCATIONS -> {
                            repository.getLocationsByStory(storyId).map { location ->
                                val name = location.profile.name.ifBlank { "(unnamed)" }
                                val desc = location.profile.description?.takeIf { it.isNotBlank() } ?: "no description"
                                EntityPreview(location.id, name, desc)
                            }
                        }
                        EntityOptionType.ARCS -> {
                            repository.getArcsByStory(storyId).map { arc ->
                                val label = arc.title.ifBlank { "(untitled)" }
                                val scope = arc.scopeType.name.lowercase()
                                EntityPreview(arc.id, label, "scope=$scope")
                            }
                        }
                        EntityOptionType.WORLD_RULES -> {
                            repository.getWorldRulesByStory(storyId).map { rule ->
                                EntityPreview(rule.id, rule.title, rule.description ?: "")
                            }
                        }
                        EntityOptionType.CULTURES -> {
                            repository.getCulturesByStory(storyId).map { culture ->
                                EntityPreview(culture.id, culture.name, culture.description ?: "")
                            }
                        }
                        EntityOptionType.EVENTS -> {
                            repository.getEventsByStory(storyId).map { event ->
                                EntityPreview(event.id, event.name, event.description ?: "")
                            }
                        }
                        EntityOptionType.ORGANIZATIONS -> {
                            repository.getOrganizationsByStory(storyId).map { org ->
                                EntityPreview(org.id, org.name, org.description ?: "")
                            }
                        }
                        EntityOptionType.RELATIONSHIPS -> {
                            repository.getRelationshipsByStory(storyId).map { rel ->
                                val subtitle = "${rel.subjectType}:${rel.subjectId} -> ${rel.objectType}:${rel.objectId}"
                                EntityPreview(rel.id, rel.relation, subtitle)
                            }
                        }
                        EntityOptionType.LOCATION_FEATURES -> {
                            repository.getLocationFeaturesByStory(storyId).map { feature ->
                                val subtitle = feature.locationId?.let { "location=$it" } ?: "no location"
                                EntityPreview(feature.id, feature.name, subtitle)
                            }
                        }
                        EntityOptionType.ARTIFACTS -> {
                            repository.getArtifactsByStory(storyId).map { artifact ->
                                val subtitle = artifact.ownerId?.let { "owner=$it" } ?: "no owner"
                                EntityPreview(artifact.id, artifact.name, subtitle)
                            }
                        }
                        EntityOptionType.TIMELINE -> {
                            repository.getTimelineEntriesByStory(storyId).map { entry ->
                                val subtitle = "order=${entry.orderIndex}"
                                EntityPreview(entry.id, entry.title, subtitle)
                            }
                        }
                        EntityOptionType.VOLUMES -> {
                            repository.getVolumesByStory(storyId).map { volume ->
                                val title = volume.title.ifBlank { "(untitled)" }
                                EntityPreview(volume.id, "Volume ${volume.number}", "title=$title")
                            }
                        }
                        EntityOptionType.CHAPTERS -> {
                            val volumes = repository.getVolumesByStory(storyId)
                            volumes.flatMap { volume ->
                                repository.getChaptersByVolume(volume.id).map { chapter ->
                                    val title = chapter.title.ifBlank { "(untitled)" }
                                    EntityPreview(chapter.id, "Ch ${chapter.number}", "vol ${volume.number} · $title")
                                }
                            }
                        }
                        EntityOptionType.SCENES -> {
                            val volumes = repository.getVolumesByStory(storyId)
                            val chapters = volumes.flatMap { volume -> repository.getChaptersByVolume(volume.id) }
                            chapters.flatMap { chapter ->
                                repository.getScenesByChapter(chapter.id).map { scene ->
                                    val title = scene.title?.ifBlank { "(untitled)" } ?: "(untitled)"
                                    EntityPreview(scene.id, "Scene ${scene.number}", "ch ${chapter.number} · $title")
                                }
                            }
                        }
                    }
                    val withCreate = if (type.supportsCreate) {
                        val (title, subtitle) = if (type == EntityOptionType.CHARACTERS) {
                            "+ Create character" to "Start a new character profile"
                        } else {
                            "+ New ${type.title}" to "Create a new ${type.title.lowercase()}"
                        }
                        listOf(EntityPreview(id = "create", title = title, subtitle = subtitle, isCreate = true)) + items
                    } else {
                        items
                    }
                    _state.update { it.copy(isLoading = false, items = withCreate) }
                }.onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load list.")
                    }
                }
            }
        }
    }
}
