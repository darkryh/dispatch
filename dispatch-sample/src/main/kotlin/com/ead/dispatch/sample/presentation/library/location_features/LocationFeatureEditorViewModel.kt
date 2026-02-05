package com.ead.dispatch.sample.presentation.library.location_features

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryLocationFeatureRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.location_feature_agent.LocationFeatureAgent
import com.ead.dispatch.sample.domain.agents.location_feature_agent.LocationFeatureAIRequest
import com.ead.dispatch.sample.domain.agents.location_feature_agent.LocationFeatureAIMode
import com.ead.dispatch.sample.domain.agents.location_feature_agent.LocationFeatureStoryContext
import com.ead.dispatch.sample.navigation.LocationFeatureEditorRoute
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.sample.presentation.editor.model.EditorAIMode
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorMode
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class LocationFeatureEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val locationFeatureAgent: LocationFeatureAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<LocationFeatureEditorRoute>()

    private val _uiState = MutableStateFlow(
        LocationFeatureEditorState(
            storyId = route.storyId,
            featureId = route.locationFeatureId,
            isLoading = route.locationFeatureId != null,
        )
    )
    val uiState: StateFlow<LocationFeatureEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val featureId = route.locationFeatureId?.trim().takeIf { !it.isNullOrEmpty() }
            if (featureId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getLocationFeaturesByStory(storyId)
                            .firstOrNull { it.id == featureId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Location feature not found.") }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    values = valuesFromRecord(record),
                                    createdAt = record.createdAt,
                                )
                            }
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(isLoading = false, error = error.message ?: "Failed to load feature.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: LocationFeatureEditorEvent) {
        when (event) {
            is LocationFeatureEditorEvent.OnToggleMode -> toggleMode()
            is LocationFeatureEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is LocationFeatureEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is LocationFeatureEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is LocationFeatureEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is LocationFeatureEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is LocationFeatureEditorEvent.OnToggleAiMode -> toggleAiMode()
            is LocationFeatureEditorEvent.OnSave -> saveFeature()
            is LocationFeatureEditorEvent.OnRequestDelete -> requestDelete()
            is LocationFeatureEditorEvent.OnConfirmDelete -> confirmDelete()
            is LocationFeatureEditorEvent.OnCancelDelete -> cancelDelete()
        }
    }

    private fun toggleMode() {
        _uiState.update { state ->
            state.copy(
                mode = if (state.mode == EditorMode.MANUAL) EditorMode.AUTOMATIC else EditorMode.MANUAL,
            )
        }
    }

    private fun updateField(key: EditorFieldKey, text: String) {
        _uiState.update { state ->
            val current = state.values[key] ?: FieldValue()
            val resetDraft = state.mode == EditorMode.AUTOMATIC &&
                (key == EditorFieldKey.PROMPT)
            state.copy(
                values = state.values + (key to current.copy(text = text)),
                aiDraft = if (resetDraft) null else state.aiDraft,
                aiError = if (resetDraft) null else state.aiError,
            )
        }
    }

    private fun generateAiDraft(regenerate: Boolean) {
        val state = _uiState.value
        if (state.mode != EditorMode.AUTOMATIC) return

        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }

        val prompt = state.values[EditorFieldKey.PROMPT]?.text?.trim().orEmpty()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(status = "Prompt is required for AI generation.") }
            return
        }

        _uiState.update {
            it.copy(
                isGenerating = true,
                aiError = null,
                status = if (regenerate) "Regenerating draft..." else "Generating draft...",
                aiDraft = if (regenerate) null else it.aiDraft,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val story = repository.getStoryById(storyId)
                val existingNames = normalizeNames(
                    repository.getLocationFeaturesByStory(storyId).map { it.name }
                )
                val locationNames = normalizeNames(
                    repository.getLocationsByStory(storyId).map { it.profile.name }
                )

                val request = LocationFeatureAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = LocationFeatureStoryContext.fromStory(story),
                    existingFeatureNames = existingNames,
                    locationNames = locationNames,
                    mode = state.aiMode.toFeatureMode(),
                )

                locationFeatureAgent.generateDraft(request)
            }.onSuccess { draft ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        aiDraft = draft,
                        aiError = null,
                        status = "Draft ready. Apply to edit.",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        aiDraft = null,
                        aiError = error.message ?: "AI generation failed.",
                        status = "AI generation failed.",
                    )
                }
            }
        }
    }

    private fun applyAiDraft() {
        val state = _uiState.value
        val draft = state.aiDraft
        if (draft == null) {
            _uiState.update { it.copy(status = "Generate a draft before applying.") }
            return
        }

        val updatedValues = LocationFeatureDraftMapper.applyDraft(draft, state.values)
        _uiState.update {
            it.copy(
                values = updatedValues,
                aiDraft = null,
                aiError = null,
                mode = EditorMode.MANUAL,
                status = "Draft applied. Review and save.",
            )
        }
    }

    private fun discardAiDraft() {
        _uiState.update { it.copy(aiDraft = null, aiError = null, status = "Draft discarded.") }
    }

    private fun toggleAiMode() {
        val next = if (_uiState.value.aiMode == EditorAIMode.NORMAL) {
            EditorAIMode.CREATIVE
        } else {
            EditorAIMode.NORMAL
        }
        _uiState.update {
            it.copy(
                aiMode = next,
                aiDraft = null,
                aiError = null,
                status = "AI mode: ${next.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }}.",
            )
        }
    }

    private fun requestDelete() {
        val currentId = _uiState.value.featureId?.trim().takeIf { !it.isNullOrEmpty() }
        if (currentId == null) {
            _uiState.update { it.copy(status = "Save before deleting.") }
            return
        }
        _uiState.update { it.copy(confirmDelete = true, status = null) }
    }

    private fun cancelDelete() {
        _uiState.update { it.copy(confirmDelete = false) }
    }

    private fun confirmDelete() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        val featureId = state.featureId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || featureId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryLocationFeature(featureId)
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Location feature deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveFeature() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }

        val name = state.values[EditorFieldKey.NAME]?.text?.trim().orEmpty()
        if (name.isBlank()) {
            _uiState.update { it.copy(status = "Name is required.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val existingId = state.featureId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }
                val locationId = state.values[EditorFieldKey.LOCATION_ID]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryLocationFeatureRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    locationId = locationId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryLocationFeature(record)
                } else {
                    repository.updateStoryLocationFeature(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        featureId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.featureId == null) "Location feature created." else "Location feature updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryLocationFeatureRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.NAME to FieldValue(record.name),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
        EditorFieldKey.LOCATION_ID to FieldValue(record.locationId.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toFeatureMode(): LocationFeatureAIMode = when (this) {
        EditorAIMode.NORMAL -> LocationFeatureAIMode.NORMAL
        EditorAIMode.CREATIVE -> LocationFeatureAIMode.CREATIVE
    }
}
