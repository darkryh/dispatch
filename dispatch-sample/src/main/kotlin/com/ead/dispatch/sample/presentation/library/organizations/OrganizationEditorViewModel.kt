package com.ead.dispatch.sample.presentation.library.organizations

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryOrganizationRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.organization_agent.OrganizationAgent
import com.ead.dispatch.sample.domain.agents.organization_agent.OrganizationAIRequest
import com.ead.dispatch.sample.domain.agents.organization_agent.OrganizationAIMode
import com.ead.dispatch.sample.domain.agents.organization_agent.OrganizationStoryContext
import com.ead.dispatch.sample.navigation.OrganizationEditorRoute
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

class OrganizationEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val organizationAgent: OrganizationAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<OrganizationEditorRoute>()

    private val _uiState = MutableStateFlow(
        OrganizationEditorState(
            storyId = route.storyId,
            organizationId = route.organizationId,
            isLoading = route.organizationId != null,
        )
    )
    val uiState: StateFlow<OrganizationEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val orgId = route.organizationId?.trim().takeIf { !it.isNullOrEmpty() }
            if (orgId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getOrganizationsByStory(storyId)
                            .firstOrNull { it.id == orgId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Organization not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load organization.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: OrganizationEditorEvent) {
        when (event) {
            is OrganizationEditorEvent.OnToggleMode -> toggleMode()
            is OrganizationEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is OrganizationEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is OrganizationEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is OrganizationEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is OrganizationEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is OrganizationEditorEvent.OnToggleAiMode -> toggleAiMode()
            is OrganizationEditorEvent.OnSave -> saveOrganization()
            is OrganizationEditorEvent.OnRequestDelete -> requestDelete()
            is OrganizationEditorEvent.OnConfirmDelete -> confirmDelete()
            is OrganizationEditorEvent.OnCancelDelete -> cancelDelete()
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
                    repository.getOrganizationsByStory(storyId).map { it.name }
                )

                val request = OrganizationAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = OrganizationStoryContext.fromStory(story),
                    existingOrganizationNames = existingNames,
                    mode = state.aiMode.toOrganizationMode(),
                )

                organizationAgent.generateDraft(request)
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

        val updatedValues = OrganizationDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.organizationId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val orgId = state.organizationId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || orgId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryOrganization(
                    storyId = storyId,
                    organizationId = orgId,
                )
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Organization deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveOrganization() {
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
                val existingId = state.organizationId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryOrganizationRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryOrganization(record)
                } else {
                    repository.updateStoryOrganization(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        organizationId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.organizationId == null) "Organization created." else "Organization updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryOrganizationRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.NAME to FieldValue(record.name),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toOrganizationMode(): OrganizationAIMode = when (this) {
        EditorAIMode.NORMAL -> OrganizationAIMode.NORMAL
        EditorAIMode.CREATIVE -> OrganizationAIMode.CREATIVE
    }
}
