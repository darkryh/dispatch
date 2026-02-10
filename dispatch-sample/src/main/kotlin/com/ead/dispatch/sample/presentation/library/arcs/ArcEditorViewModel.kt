package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.navigation.ArcEditorRoute
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ArcEditorViewModel(
    private val repository: StructuredIndexRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ArcEditorRoute>()

    private val _uiState = MutableStateFlow(
        ArcEditorState(
            storyId = route.storyId,
            arcId = route.arcId,
            isLoading = route.arcId != null,
        )
    )
    val uiState: StateFlow<ArcEditorState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val arcId = route.arcId?.trim().takeIf { !it.isNullOrEmpty() }
            if (arcId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        repository.getArcsByStory(storyId)
                            .firstOrNull { it.id == arcId }
                    }.onSuccess { record ->
                        if (record == null) {
                            _uiState.update { it.copy(isLoading = false, error = "Arc not found.") }
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
                            it.copy(isLoading = false, error = error.message ?: "Failed to load arc.")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: ArcEditorEvent) {
        when (event) {
            is ArcEditorEvent.OnFieldChanged -> updateField(event.key, event.text)
            is ArcEditorEvent.OnSave -> saveArc()
            is ArcEditorEvent.OnRequestDelete -> requestDelete()
            is ArcEditorEvent.OnConfirmDelete -> confirmDelete()
            is ArcEditorEvent.OnCancelDelete -> cancelDelete()
        }
    }

    private fun updateField(key: EditorFieldKey, text: String) {
        _uiState.update { state ->
            val current = state.values[key] ?: FieldValue()
            state.copy(values = state.values + (key to current.copy(text = text)))
        }
    }

    private fun requestDelete() {
        val currentId = _uiState.value.arcId?.trim().takeIf { !it.isNullOrEmpty() }
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
        val arcId = state.arcId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || arcId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryArc(
                    storyId = storyId,
                    arcId = arcId,
                )
            }.onSuccess {
                _uiState.update { it.copy(confirmDelete = false, status = "Arc deleted.") }
            }.onFailure { error ->
                _uiState.update { it.copy(confirmDelete = false, status = error.message ?: "Delete failed.") }
            }
        }
    }

    private fun saveArc() {
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
                val existingId = state.arcId?.trim().takeIf { !it.isNullOrEmpty() }
                val createdAt = state.createdAt ?: now
                val summary = state.values[EditorFieldKey.SUMMARY]?.text?.trim()?.takeIf { it.isNotBlank() }
                val scopeTypeValue = state.values[EditorFieldKey.SCOPE_TYPE]?.text?.trim().orEmpty().ifBlank { "STORY" }
                val scopeType = parseArcScope(scopeTypeValue)
                    ?: throw IllegalArgumentException("Invalid scope type.")
                val scopeId = state.values[EditorFieldKey.SCOPE_ID]?.text?.trim()?.takeIf { it.isNotBlank() }
                if (scopeType != ArcScope.STORY && scopeId == null) {
                    throw IllegalArgumentException("Scope id is required for ${scopeType.name}.")
                }
                val statusValue = state.values[EditorFieldKey.STATUS]?.text?.trim()?.takeIf { it.isNotBlank() }
                val status = statusValue?.let { parseStatus(it) }
                if (statusValue != null && status == null) {
                    throw IllegalArgumentException("Invalid status value.")
                }

                val record = StoryArcRecord(
                    id = existingId ?: UUID.randomUUID().toString(),
                    storyId = storyId,
                    scopeType = scopeType,
                    scopeId = scopeId,
                    title = title,
                    summary = summary,
                    status = status,
                    createdAt = createdAt,
                    updatedAt = now,
                )

                repository.upsertArc(record)

                record
            }.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        arcId = record.id,
                        createdAt = record.createdAt,
                        status = if (state.arcId == null) "Arc created." else "Arc updated.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(record: StoryArcRecord): Map<EditorFieldKey, FieldValue> = mapOf(
        EditorFieldKey.TITLE to FieldValue(record.title),
        EditorFieldKey.SUMMARY to FieldValue(record.summary.orEmpty()),
        EditorFieldKey.SCOPE_TYPE to FieldValue(record.scopeType.name),
        EditorFieldKey.SCOPE_ID to FieldValue(record.scopeId.orEmpty()),
        EditorFieldKey.STATUS to FieldValue(record.status?.name.orEmpty()),
    )

    private fun parseArcScope(value: String): ArcScope? =
        ArcScope.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    private fun parseStatus(value: String): ContentStatus? =
        ContentStatus.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
}
