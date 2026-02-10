package com.ead.dispatch.sample.presentation.library.world_rules

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryWorldRuleRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleAgent
import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleAIRequest
import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleAIMode
import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleStoryContext
import com.ead.dispatch.sample.navigation.WorldRuleEditorRoute
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

class WorldRuleEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val worldRuleAgent: WorldRuleAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<WorldRuleEditorRoute>()

    private val _uiState = MutableStateFlow(
        WorldRuleEditorState(
            storyId = route.storyId,
            worldRuleId = route.worldRuleId,
            isLoading = route.worldRuleId != null,
        )
    )
    val uiState: StateFlow<WorldRuleEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val ruleId = route.worldRuleId?.trim().takeIf { !it.isNullOrEmpty() }
            if (ruleId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getWorldRulesByStory(storyId)
                            .firstOrNull { it.id == ruleId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "World rule not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load world rule.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: WorldRuleEditorEvent) {
        when (event) {
            is WorldRuleEditorEvent.OnToggleMode -> toggleMode()
            is WorldRuleEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is WorldRuleEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is WorldRuleEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is WorldRuleEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is WorldRuleEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is WorldRuleEditorEvent.OnToggleAiMode -> toggleAiMode()
            is WorldRuleEditorEvent.OnSave -> saveWorldRule()
            is WorldRuleEditorEvent.OnRequestDelete -> requestDelete()
            is WorldRuleEditorEvent.OnConfirmDelete -> confirmDelete()
            is WorldRuleEditorEvent.OnCancelDelete -> cancelDelete()
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
                val existingTitles = normalizeNames(
                    repository.getWorldRulesByStory(storyId).map { it.title }
                )

                val request = WorldRuleAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = WorldRuleStoryContext.fromStory(story),
                    existingRuleTitles = existingTitles,
                    mode = state.aiMode.toWorldRuleMode(),
                )

                worldRuleAgent.generateDraft(request)
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

        val updatedValues = WorldRuleDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.worldRuleId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val ruleId = state.worldRuleId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || ruleId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryWorldRule(
                    storyId = storyId,
                    ruleId = ruleId,
                )
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "World rule deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveWorldRule() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }

        val title = state.values[EditorFieldKey.TITLE]?.text?.trim().orEmpty()
        if (title.isBlank()) {
            _uiState.update { it.copy(status = "Title is required.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val existingId = state.worldRuleId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryWorldRuleRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    title = title,
                    description = description,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryWorldRule(record)
                } else {
                    repository.updateStoryWorldRule(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        worldRuleId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.worldRuleId == null) "World rule created." else "World rule updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryWorldRuleRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.TITLE to FieldValue(record.title),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toWorldRuleMode(): WorldRuleAIMode = when (this) {
        EditorAIMode.NORMAL -> WorldRuleAIMode.NORMAL
        EditorAIMode.CREATIVE -> WorldRuleAIMode.CREATIVE
    }
}
