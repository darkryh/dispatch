package com.ead.dispatch.sample.presentation.entity_editor.event

import com.ead.dispatch.sample.presentation.entity_editor.util.EntityFieldKey

sealed interface EntityEditorEvent {
    data class OnFieldChanged(val key: EntityFieldKey, val text: String) : EntityEditorEvent
    data object OnToggleMode : EntityEditorEvent
    data object OnSave : EntityEditorEvent
    data object OnRequestDelete : EntityEditorEvent
    data object OnConfirmDelete : EntityEditorEvent
    data object OnCancelDelete : EntityEditorEvent
}
