package com.ead.dispatch.sample.presentation.library.world_rules

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey

sealed interface WorldRuleEditorEvent {
    data class OnFieldChanged(val key: EditorFieldKey, val text: String) : WorldRuleEditorEvent
    data object OnToggleMode : WorldRuleEditorEvent
    data object OnGenerateAiDraft : WorldRuleEditorEvent
    data object OnRegenerateAiDraft : WorldRuleEditorEvent
    data object OnApplyAiDraft : WorldRuleEditorEvent
    data object OnDiscardAiDraft : WorldRuleEditorEvent
    data object OnToggleAiMode : WorldRuleEditorEvent
    data object OnSave : WorldRuleEditorEvent
    data object OnRequestDelete : WorldRuleEditorEvent
    data object OnConfirmDelete : WorldRuleEditorEvent
    data object OnCancelDelete : WorldRuleEditorEvent
}
