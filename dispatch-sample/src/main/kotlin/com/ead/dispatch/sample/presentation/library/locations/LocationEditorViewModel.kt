package com.ead.dispatch.sample.presentation.library.locations

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.location_agent.LocationAgent
import com.ead.dispatch.sample.domain.agents.location_agent.LocationAIRequest
import com.ead.dispatch.sample.domain.agents.location_agent.LocationAIMode
import com.ead.dispatch.sample.domain.agents.location_agent.LocationStoryContext
import com.ead.dispatch.sample.navigation.LocationEditorRoute
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

class LocationEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val locationAgent: LocationAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<LocationEditorRoute>()

    private val _uiState = MutableStateFlow(
        LocationEditorState(
            storyId = route.storyId,
            locationId = route.locationId,
            isLoading = route.locationId != null,
        )
    )
    val uiState: StateFlow<LocationEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val locationId = route.locationId?.trim().takeIf { !it.isNullOrEmpty() }
            if (locationId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getLocationsByStory(storyId)
                            .firstOrNull { it.id == locationId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Location not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load location.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: LocationEditorEvent) {
        when (event) {
            is LocationEditorEvent.OnToggleMode -> toggleMode()
            is LocationEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is LocationEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is LocationEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is LocationEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is LocationEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is LocationEditorEvent.OnToggleAiMode -> toggleAiMode()
            is LocationEditorEvent.OnSave -> saveLocation()
            is LocationEditorEvent.OnRequestDelete -> requestDelete()
            is LocationEditorEvent.OnConfirmDelete -> confirmDelete()
            is LocationEditorEvent.OnCancelDelete -> cancelDelete()
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
                    repository.getLocationsByStory(storyId).map { it.profile.name }
                )

                val request = LocationAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = LocationStoryContext.fromStory(story),
                    existingLocationNames = existingNames,
                    mode = state.aiMode.toLocationMode(),
                )

                locationAgent.generateDraft(request)
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

        val updatedValues = LocationDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.locationId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val locationId = state.locationId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || locationId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryLocation(locationId)
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Location deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveLocation() {
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
                val existingId = state.locationId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryLocationRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    profile = StoryLocationRecord.LocationProfile(
                        name = name,
                        description = description,
                    ),
                    tags = parseTags(state.values[EditorFieldKey.TAGS]?.text?.trim().orEmpty()),
                    createdAt = createdAt,
                )

                repository.upsertLocation(record)

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        locationId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.locationId == null) "Location created." else "Location updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryLocationRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.NAME to FieldValue(record.profile.name),
        EditorFieldKey.DESCRIPTION to FieldValue(record.profile.description.orEmpty()),
        EditorFieldKey.TAGS to FieldValue(record.tags.joinToString(", ")),
    )

    private fun parseTags(raw: String): List<String> =
        raw.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toLocationMode(): LocationAIMode = when (this) {
        EditorAIMode.NORMAL -> LocationAIMode.NORMAL
        EditorAIMode.CREATIVE -> LocationAIMode.CREATIVE
    }
}
