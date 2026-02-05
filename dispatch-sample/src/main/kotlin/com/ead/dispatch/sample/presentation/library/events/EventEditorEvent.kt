package com.ead.dispatch.sample.presentation.library.events

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface EventEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : EventEditorEvent
    data object OnToggleMode : EventEditorEvent
    data object OnGenerateAiDraft : EventEditorEvent
    data object OnRegenerateAiDraft : EventEditorEvent
    data object OnApplyAiDraft : EventEditorEvent
    data object OnDiscardAiDraft : EventEditorEvent
    data object OnToggleAiMode : EventEditorEvent
    data object OnSave : EventEditorEvent
    data object OnRequestDelete : EventEditorEvent
    data object OnConfirmDelete : EventEditorEvent
    data object OnCancelDelete : EventEditorEvent
}
