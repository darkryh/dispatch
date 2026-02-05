package com.ead.dispatch.sample.presentation.library.cultures

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface CultureEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : CultureEditorEvent
    data object OnToggleMode : CultureEditorEvent
    data object OnGenerateAiDraft : CultureEditorEvent
    data object OnRegenerateAiDraft : CultureEditorEvent
    data object OnApplyAiDraft : CultureEditorEvent
    data object OnDiscardAiDraft : CultureEditorEvent
    data object OnToggleAiMode : CultureEditorEvent
    data object OnSave : CultureEditorEvent
    data object OnRequestDelete : CultureEditorEvent
    data object OnConfirmDelete : CultureEditorEvent
    data object OnCancelDelete : CultureEditorEvent
}
