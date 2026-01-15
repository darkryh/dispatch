package com.ead.dispatch.sample.presentation.characters.event

import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey

sealed class CharacterEvent {
    data object OnToggleMode : CharacterEvent()
    data class OnFieldChanged(val key: CharacterFieldKey, val text: String) : CharacterEvent()
}
