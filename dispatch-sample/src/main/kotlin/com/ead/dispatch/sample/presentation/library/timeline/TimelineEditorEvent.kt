package com.ead.dispatch.sample.presentation.library.timeline

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface TimelineEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : TimelineEditorEvent
    data object OnToggleMode : TimelineEditorEvent
    data object OnGenerateAiDraft : TimelineEditorEvent
    data object OnRegenerateAiDraft : TimelineEditorEvent
    data object OnApplyAiDraft : TimelineEditorEvent
    data object OnDiscardAiDraft : TimelineEditorEvent
    data object OnToggleAiMode : TimelineEditorEvent
    data object OnSave : TimelineEditorEvent
    data object OnRequestDelete : TimelineEditorEvent
    data object OnConfirmDelete : TimelineEditorEvent
    data object OnCancelDelete : TimelineEditorEvent
}
