package com.ead.dispatch.sample.presentation.library.organizations

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface OrganizationEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : OrganizationEditorEvent
    data object OnToggleMode : OrganizationEditorEvent
    data object OnGenerateAiDraft : OrganizationEditorEvent
    data object OnRegenerateAiDraft : OrganizationEditorEvent
    data object OnApplyAiDraft : OrganizationEditorEvent
    data object OnDiscardAiDraft : OrganizationEditorEvent
    data object OnToggleAiMode : OrganizationEditorEvent
    data object OnSave : OrganizationEditorEvent
    data object OnRequestDelete : OrganizationEditorEvent
    data object OnConfirmDelete : OrganizationEditorEvent
    data object OnCancelDelete : OrganizationEditorEvent
}
