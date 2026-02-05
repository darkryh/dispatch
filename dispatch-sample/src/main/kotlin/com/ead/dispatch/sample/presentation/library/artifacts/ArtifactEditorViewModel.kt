package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryArtifactRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAgent
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIRequest
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIMode
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactStoryContext
import com.ead.dispatch.sample.navigation.ArtifactEditorRoute
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

class ArtifactEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val artifactAgent: ArtifactAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ArtifactEditorRoute>()

    private val _uiState = MutableStateFlow(
        ArtifactEditorState(
            storyId = route.storyId,
            artifactId = route.artifactId,
            isLoading = route.artifactId != null,
        )
    )
    val uiState: StateFlow<ArtifactEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val artifactId = route.artifactId?.trim().takeIf { !it.isNullOrEmpty() }
            if (artifactId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getArtifactsByStory(storyId)
                            .firstOrNull { it.id == artifactId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Artifact not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load artifact.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: ArtifactEditorEvent) {
        when (event) {
            is ArtifactEditorEvent.OnToggleMode -> toggleMode()
            is ArtifactEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is ArtifactEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is ArtifactEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is ArtifactEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is ArtifactEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is ArtifactEditorEvent.OnToggleAiMode -> toggleAiMode()
            is ArtifactEditorEvent.OnSave -> saveArtifact()
            is ArtifactEditorEvent.OnRequestDelete -> requestDelete()
            is ArtifactEditorEvent.OnConfirmDelete -> confirmDelete()
            is ArtifactEditorEvent.OnCancelDelete -> cancelDelete()
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
                (key == EditorFieldKey.PROMPT || key == EditorFieldKey.CONSTRAINTS)
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

        val constraints = state.values[EditorFieldKey.CONSTRAINTS]?.text?.trim()?.takeIf { it.isNotEmpty() }

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
                    repository.getArtifactsByStory(storyId).map { it.name }
                )
                val characterNames = normalizeNames(
                    repository.getStoryCharacters(storyId).map { it.name }
                )
                val organizationNames = normalizeNames(
                    repository.getOrganizationsByStory(storyId).map { it.name }
                )
                val locationNames = normalizeNames(
                    repository.getLocationsByStory(storyId).map { it.profile.name }
                )

                val request = ArtifactAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    constraints = constraints,
                    story = ArtifactStoryContext.fromStory(story),
                    existingArtifactNames = existingNames,
                    characterNames = characterNames,
                    organizationNames = organizationNames,
                    locationNames = locationNames,
                    mode = state.aiMode.toArtifactMode(),
                )

                artifactAgent.generateDraft(request)
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

        val updatedValues = ArtifactDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.artifactId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val artifactId = state.artifactId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || artifactId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryArtifact(artifactId)
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Artifact deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveArtifact() {
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
                val existingId = state.artifactId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }
                val ownerType = state.values[EditorFieldKey.OWNER_TYPE]?.text?.trim()?.takeIf { it.isNotBlank() }
                val ownerId = state.values[EditorFieldKey.OWNER_ID]?.text?.trim()?.takeIf { it.isNotBlank() }
                val locationId = state.values[EditorFieldKey.LOCATION_ID]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryArtifactRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    name = name,
                    description = description,
                    ownerId = ownerId,
                    ownerType = ownerType,
                    locationId = locationId,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryArtifact(record)
                } else {
                    repository.updateStoryArtifact(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        artifactId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.artifactId == null) "Artifact created." else "Artifact updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryArtifactRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.NAME to FieldValue(record.name),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
        EditorFieldKey.OWNER_TYPE to FieldValue(record.ownerType.orEmpty()),
        EditorFieldKey.OWNER_ID to FieldValue(record.ownerId.orEmpty()),
        EditorFieldKey.LOCATION_ID to FieldValue(record.locationId.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toArtifactMode(): ArtifactAIMode = when (this) {
        EditorAIMode.NORMAL -> ArtifactAIMode.NORMAL
        EditorAIMode.CREATIVE -> ArtifactAIMode.CREATIVE
    }
}
