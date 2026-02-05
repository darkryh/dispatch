package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface ArtifactEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : ArtifactEditorEvent
    data object OnToggleMode : ArtifactEditorEvent
    data object OnGenerateAiDraft : ArtifactEditorEvent
    data object OnRegenerateAiDraft : ArtifactEditorEvent
    data object OnApplyAiDraft : ArtifactEditorEvent
    data object OnDiscardAiDraft : ArtifactEditorEvent
    data object OnToggleAiMode : ArtifactEditorEvent
    data object OnSave : ArtifactEditorEvent
    data object OnRequestDelete : ArtifactEditorEvent
    data object OnConfirmDelete : ArtifactEditorEvent
    data object OnCancelDelete : ArtifactEditorEvent
}
