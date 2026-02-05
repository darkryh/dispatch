package com.ead.dispatch.sample.presentation.library.locations

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface LocationEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : LocationEditorEvent
    data object OnToggleMode : LocationEditorEvent
    data object OnGenerateAiDraft : LocationEditorEvent
    data object OnRegenerateAiDraft : LocationEditorEvent
    data object OnApplyAiDraft : LocationEditorEvent
    data object OnDiscardAiDraft : LocationEditorEvent
    data object OnToggleAiMode : LocationEditorEvent
    data object OnSave : LocationEditorEvent
    data object OnRequestDelete : LocationEditorEvent
    data object OnConfirmDelete : LocationEditorEvent
    data object OnCancelDelete : LocationEditorEvent
}
