package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface ArcEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : ArcEditorEvent
    data object OnSave : ArcEditorEvent
    data object OnRequestDelete : ArcEditorEvent
    data object OnConfirmDelete : ArcEditorEvent
    data object OnCancelDelete : ArcEditorEvent
}
