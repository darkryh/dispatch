package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryRelationshipRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.relationship_agent.RelationshipAgent
import com.ead.dispatch.sample.domain.agents.relationship_agent.RelationshipAIRequest
import com.ead.dispatch.sample.domain.agents.relationship_agent.RelationshipAIMode
import com.ead.dispatch.sample.domain.agents.relationship_agent.RelationshipStoryContext
import com.ead.dispatch.sample.navigation.RelationshipEditorRoute
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

class RelationshipEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val relationshipAgent: RelationshipAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<RelationshipEditorRoute>()

    private val _uiState = MutableStateFlow(
        RelationshipEditorState(
            storyId = route.storyId,
            relationshipId = route.relationshipId,
            isLoading = route.relationshipId != null,
        )
    )
    val uiState: StateFlow<RelationshipEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val relationshipId = route.relationshipId?.trim().takeIf { !it.isNullOrEmpty() }
            if (relationshipId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getRelationshipsByStory(storyId)
                            .firstOrNull { it.id == relationshipId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Relationship not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load relationship.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: RelationshipEditorEvent) {
        when (event) {
            is RelationshipEditorEvent.OnToggleMode -> toggleMode()
            is RelationshipEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is RelationshipEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is RelationshipEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is RelationshipEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is RelationshipEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is RelationshipEditorEvent.OnToggleAiMode -> toggleAiMode()
            is RelationshipEditorEvent.OnSave -> saveRelationship()
            is RelationshipEditorEvent.OnRequestDelete -> requestDelete()
            is RelationshipEditorEvent.OnConfirmDelete -> confirmDelete()
            is RelationshipEditorEvent.OnCancelDelete -> cancelDelete()
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
                val characterNames = normalizeNames(
                    repository.getStoryCharacters(storyId).map { it.name }
                )
                val organizationNames = normalizeNames(
                    repository.getOrganizationsByStory(storyId).map { it.name }
                )
                val locationNames = normalizeNames(
                    repository.getLocationsByStory(storyId).map { it.profile.name }
                )

                val request = RelationshipAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = RelationshipStoryContext.fromStory(story),
                    characterNames = characterNames,
                    organizationNames = organizationNames,
                    locationNames = locationNames,
                    mode = state.aiMode.toRelationshipMode(),
                )

                relationshipAgent.generateDraft(request)
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

        val updatedValues = RelationshipDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.relationshipId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val relationshipId = state.relationshipId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || relationshipId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryRelationship(
                    storyId = storyId,
                    relationshipId = relationshipId,
                )
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Relationship deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveRelationship() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }

        val subjectType = state.values[EditorFieldKey.SUBJECT_TYPE]?.text?.trim().orEmpty()
        val subjectId = state.values[EditorFieldKey.SUBJECT_ID]?.text?.trim().orEmpty()
        val objectType = state.values[EditorFieldKey.OBJECT_TYPE]?.text?.trim().orEmpty()
        val objectId = state.values[EditorFieldKey.OBJECT_ID]?.text?.trim().orEmpty()
        val relation = state.values[EditorFieldKey.RELATION]?.text?.trim().orEmpty()
        if (subjectType.isBlank() || subjectId.isBlank() || objectType.isBlank() || objectId.isBlank() || relation.isBlank()) {
            _uiState.update { it.copy(status = "Subject/object and relation are required.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val existingId = state.relationshipId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val notes = state.values[EditorFieldKey.NOTES]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryRelationshipRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    subjectId = subjectId,
                    subjectType = subjectType,
                    objectId = objectId,
                    objectType = objectType,
                    relation = relation,
                    notes = notes,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryRelationship(record)
                } else {
                    repository.updateStoryRelationship(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        relationshipId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.relationshipId == null) "Relationship created." else "Relationship updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryRelationshipRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.SUBJECT_TYPE to FieldValue(record.subjectType),
        EditorFieldKey.SUBJECT_ID to FieldValue(record.subjectId),
        EditorFieldKey.OBJECT_TYPE to FieldValue(record.objectType),
        EditorFieldKey.OBJECT_ID to FieldValue(record.objectId),
        EditorFieldKey.RELATION to FieldValue(record.relation),
        EditorFieldKey.NOTES to FieldValue(record.notes.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toRelationshipMode(): RelationshipAIMode = when (this) {
        EditorAIMode.NORMAL -> RelationshipAIMode.NORMAL
        EditorAIMode.CREATIVE -> RelationshipAIMode.CREATIVE
    }
}
