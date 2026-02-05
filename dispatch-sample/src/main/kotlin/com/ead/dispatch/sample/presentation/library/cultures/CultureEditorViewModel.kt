package com.ead.dispatch.sample.presentation.library.cultures

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryCultureRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.culture_agent.CultureAgent
import com.ead.dispatch.sample.domain.agents.culture_agent.CultureAIRequest
import com.ead.dispatch.sample.domain.agents.culture_agent.CultureAIMode
import com.ead.dispatch.sample.domain.agents.culture_agent.CultureStoryContext
import com.ead.dispatch.sample.navigation.CultureEditorRoute
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

class CultureEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val cultureAgent: CultureAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CultureEditorRoute>()

    private val _uiState = MutableStateFlow(
        CultureEditorState(
            storyId = route.storyId,
            cultureId = route.cultureId,
            isLoading = route.cultureId != null,
        )
    )
    val uiState: StateFlow<CultureEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val cultureId = route.cultureId?.trim().takeIf { !it.isNullOrEmpty() }
            if (cultureId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getCulturesByStory(storyId)
                            .firstOrNull { it.id == cultureId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Culture not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load culture.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: CultureEditorEvent) {
        when (event) {
            is CultureEditorEvent.OnToggleMode -> toggleMode()
            is CultureEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is CultureEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is CultureEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is CultureEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is CultureEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is CultureEditorEvent.OnToggleAiMode -> toggleAiMode()
            is CultureEditorEvent.OnSave -> saveCulture()
            is CultureEditorEvent.OnRequestDelete -> requestDelete()
            is CultureEditorEvent.OnConfirmDelete -> confirmDelete()
            is CultureEditorEvent.OnCancelDelete -> cancelDelete()
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
                    repository.getCulturesByStory(storyId).map { it.name }
                )

                val request = CultureAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = CultureStoryContext.fromStory(story),
                    existingCultureNames = existingNames,
                    mode = state.aiMode.toCultureMode(),
                )

                cultureAgent.generateDraft(request)
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

        val updatedValues = CultureDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.cultureId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val cultureId = state.cultureId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || cultureId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryCulture(cultureId)
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Culture deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveCulture() {
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
                val existingId = state.cultureId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryCultureRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryCulture(record)
                } else {
                    repository.updateStoryCulture(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        cultureId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.cultureId == null) "Culture created." else "Culture updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryCultureRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.NAME to FieldValue(record.name),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toCultureMode(): CultureAIMode = when (this) {
        EditorAIMode.NORMAL -> CultureAIMode.NORMAL
        EditorAIMode.CREATIVE -> CultureAIMode.CREATIVE
    }
}
