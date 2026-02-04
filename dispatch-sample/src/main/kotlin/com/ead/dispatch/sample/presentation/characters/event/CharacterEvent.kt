package com.ead.dispatch.sample.presentation.characters.event

import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey

sealed class CharacterEvent {
    data object OnToggleMode : CharacterEvent()
    data class OnFieldChanged(val key: CharacterFieldKey, val text: String) : CharacterEvent()
    data object OnGenerateAiDraft : CharacterEvent()
    data object OnApplyAiDraft : CharacterEvent()
    data object OnDiscardAiDraft : CharacterEvent()
    data object OnRegenerateAiDraft : CharacterEvent()
    data object OnToggleAiMode : CharacterEvent()
    data object OnSave : CharacterEvent()
    data object OnRequestDelete : CharacterEvent()
    data object OnConfirmDelete : CharacterEvent()
    data object OnCancelDelete : CharacterEvent()
}
