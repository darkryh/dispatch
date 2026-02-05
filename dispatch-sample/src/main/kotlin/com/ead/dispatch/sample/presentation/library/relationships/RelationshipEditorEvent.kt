package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface RelationshipEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : RelationshipEditorEvent
    data object OnToggleMode : RelationshipEditorEvent
    data object OnGenerateAiDraft : RelationshipEditorEvent
    data object OnRegenerateAiDraft : RelationshipEditorEvent
    data object OnApplyAiDraft : RelationshipEditorEvent
    data object OnDiscardAiDraft : RelationshipEditorEvent
    data object OnToggleAiMode : RelationshipEditorEvent
    data object OnSave : RelationshipEditorEvent
    data object OnRequestDelete : RelationshipEditorEvent
    data object OnConfirmDelete : RelationshipEditorEvent
    data object OnCancelDelete : RelationshipEditorEvent
}
