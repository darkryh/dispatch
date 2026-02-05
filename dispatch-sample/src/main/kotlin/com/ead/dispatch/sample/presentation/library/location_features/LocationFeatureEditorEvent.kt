package com.ead.dispatch.sample.presentation.library.location_features

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface LocationFeatureEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : LocationFeatureEditorEvent
    data object OnToggleMode : LocationFeatureEditorEvent
    data object OnGenerateAiDraft : LocationFeatureEditorEvent
    data object OnRegenerateAiDraft : LocationFeatureEditorEvent
    data object OnApplyAiDraft : LocationFeatureEditorEvent
    data object OnDiscardAiDraft : LocationFeatureEditorEvent
    data object OnToggleAiMode : LocationFeatureEditorEvent
    data object OnSave : LocationFeatureEditorEvent
    data object OnRequestDelete : LocationFeatureEditorEvent
    data object OnConfirmDelete : LocationFeatureEditorEvent
    data object OnCancelDelete : LocationFeatureEditorEvent
}
