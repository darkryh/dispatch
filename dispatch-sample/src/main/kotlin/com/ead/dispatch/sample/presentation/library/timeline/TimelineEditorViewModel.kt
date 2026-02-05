package com.ead.dispatch.sample.presentation.library.timeline

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryTimelineEntryRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.timeline_agent.TimelineAgent
import com.ead.dispatch.sample.domain.agents.timeline_agent.TimelineAIRequest
import com.ead.dispatch.sample.domain.agents.timeline_agent.TimelineAIMode
import com.ead.dispatch.sample.domain.agents.timeline_agent.TimelineStoryContext
import com.ead.dispatch.sample.navigation.TimelineEditorRoute
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

class TimelineEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val timelineAgent: TimelineAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TimelineEditorRoute>()

    private val _uiState = MutableStateFlow(
        TimelineEditorState(
            storyId = route.storyId,
            entryId = route.timelineEntryId,
            isLoading = route.timelineEntryId != null,
        )
    )
    val uiState: StateFlow<TimelineEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val entryId = route.timelineEntryId?.trim().takeIf { !it.isNullOrEmpty() }
            if (entryId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getTimelineEntriesByStory(storyId)
                            .firstOrNull { it.id == entryId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Timeline entry not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load timeline entry.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: TimelineEditorEvent) {
        when (event) {
            is TimelineEditorEvent.OnToggleMode -> toggleMode()
            is TimelineEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is TimelineEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is TimelineEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is TimelineEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is TimelineEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is TimelineEditorEvent.OnToggleAiMode -> toggleAiMode()
            is TimelineEditorEvent.OnSave -> saveEntry()
            is TimelineEditorEvent.OnRequestDelete -> requestDelete()
            is TimelineEditorEvent.OnConfirmDelete -> confirmDelete()
            is TimelineEditorEvent.OnCancelDelete -> cancelDelete()
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
                val existingTitles = normalizeNames(
                    repository.getTimelineEntriesByStory(storyId).map { it.title }
                )

                val request = TimelineAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    constraints = constraints,
                    story = TimelineStoryContext.fromStory(story),
                    existingTimelineTitles = existingTitles,
                    mode = state.aiMode.toTimelineMode(),
                )

                timelineAgent.generateDraft(request)
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

        val updatedValues = TimelineDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.entryId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val entryId = state.entryId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || entryId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryTimelineEntry(entryId)
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Timeline entry deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveEntry() {
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
                val existingId = state.entryId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }
                val orderInput = state.values[EditorFieldKey.ORDER_INDEX]?.text?.trim().orEmpty()
                val orderIndex = if (orderInput.isBlank()) {
                    if (existingId == null) {
                        nextTimelineOrderIndex(storyId)
                    } else {
                        throw IllegalArgumentException("Order index is required.")
                    }
                } else {
                    orderInput.toLongOrNull() ?: throw IllegalArgumentException("Order index must be a number.")
                }

                val record = StoryTimelineEntryRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    title = title,
                    description = description,
                    orderIndex = orderIndex,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryTimelineEntry(record)
                } else {
                    repository.updateStoryTimelineEntry(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        entryId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.entryId == null) "Timeline entry created." else "Timeline entry updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryTimelineEntryRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.TITLE to FieldValue(record.title),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
        EditorFieldKey.ORDER_INDEX to FieldValue(record.orderIndex.toString()),
    )

    private suspend fun nextTimelineOrderIndex(storyId: String): Long {
        val entries = repository.getTimelineEntriesByStory(storyId)
        val max = entries.maxOfOrNull { it.orderIndex } ?: 0L
        return max + 1
    }

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toTimelineMode(): TimelineAIMode = when (this) {
        EditorAIMode.NORMAL -> TimelineAIMode.NORMAL
        EditorAIMode.CREATIVE -> TimelineAIMode.CREATIVE
    }
}
