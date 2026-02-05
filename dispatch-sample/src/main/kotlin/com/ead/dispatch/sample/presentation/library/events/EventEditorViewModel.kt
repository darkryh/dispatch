package com.ead.dispatch.sample.presentation.library.events

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryEventRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.event_agent.EventAgent
import com.ead.dispatch.sample.domain.agents.event_agent.EventAIRequest
import com.ead.dispatch.sample.domain.agents.event_agent.EventAIMode
import com.ead.dispatch.sample.domain.agents.event_agent.EventStoryContext
import com.ead.dispatch.sample.navigation.EventEditorRoute
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

class EventEditorViewModel(
    private val repository: StructuredIndexRepository,
    private val eventAgent: EventAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EventEditorRoute>()

    private val _uiState = MutableStateFlow(
        EventEditorState(
            storyId = route.storyId,
            eventId = route.eventId,
            isLoading = route.eventId != null,
        )
    )
    val uiState: StateFlow<EventEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val eventId = route.eventId?.trim().takeIf { !it.isNullOrEmpty() }
            if (eventId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getEventsByStory(storyId)
                            .firstOrNull { it.id == eventId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Event not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load event.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: EventEditorEvent) {
        when (event) {
            is EventEditorEvent.OnToggleMode -> toggleMode()
            is EventEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is EventEditorEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is EventEditorEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is EventEditorEvent.OnApplyAiDraft -> applyAiDraft()
            is EventEditorEvent.OnDiscardAiDraft -> discardAiDraft()
            is EventEditorEvent.OnToggleAiMode -> toggleAiMode()
            is EventEditorEvent.OnSave -> saveEvent()
            is EventEditorEvent.OnRequestDelete -> requestDelete()
            is EventEditorEvent.OnConfirmDelete -> confirmDelete()
            is EventEditorEvent.OnCancelDelete -> cancelDelete()
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
                    repository.getEventsByStory(storyId).map { it.name }
                )

                val request = EventAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    story = EventStoryContext.fromStory(story),
                    existingEventNames = existingNames,
                    locationNames = normalizeNames(repository.getLocationsByStory(storyId).map { it.profile.name }),
                    organizationNames = normalizeNames(repository.getOrganizationsByStory(storyId).map { it.name }),
                    mode = state.aiMode.toEventMode(),
                )

                eventAgent.generateDraft(request)
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

        val updatedValues = EventDraftMapper.applyDraft(draft, state.values)
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
        val currentId = _uiState.value.eventId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val eventId = state.eventId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || eventId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryEvent(eventId)
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Event deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveEvent() {
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
                val existingId = state.eventId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val description = state.values[EditorFieldKey.DESCRIPTION]?.text?.trim()?.takeIf { it.isNotBlank() }

                val record = StoryEventRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    name = name,
                    description = description,
                    createdAt = createdAt,
                )

                if (existingId == null) {
                    repository.insertStoryEvent(record)
                } else {
                    repository.updateStoryEvent(record)
                }

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        eventId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.eventId == null) "Event created." else "Event updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryEventRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.NAME to FieldValue(record.name),
        EditorFieldKey.DESCRIPTION to FieldValue(record.description.orEmpty()),
    )

    private fun normalizeNames(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun EditorAIMode.toEventMode(): EventAIMode = when (this) {
        EditorAIMode.NORMAL -> EventAIMode.NORMAL
        EditorAIMode.CREATIVE -> EventAIMode.CREATIVE
    }
}
