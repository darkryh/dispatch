package com.ead.dispatch.sample.presentation.characters

import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.ead.dispatch.sample.presentation.characters.event.CharacterEvent
import com.ead.dispatch.sample.presentation.characters.state.CharacterUIState
import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey
import com.ead.dispatch.sample.presentation.characters.util.CharacterUIMode
import com.ead.dispatch.sample.presentation.util.FieldValue

class CharacterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterUIState())
    val uiState: StateFlow<CharacterUIState> = _uiState.asStateFlow()

    private fun toggleMode() {
        _uiState.update { state ->
            state.copy(
                mode = if (state.mode == CharacterUIMode.MANUAL) CharacterUIMode.AUTOMATIC else CharacterUIMode.MANUAL,
            )
        }
    }

    private fun updateField(key: CharacterFieldKey, text: String) {
        _uiState.update { state ->
            val current = state.values[key] ?: FieldValue()
            state.copy(values = state.values + (key to current.copy(text = text)))
        }
    }

    fun onEvent(event: CharacterEvent) {
        when (event) {
            is CharacterEvent.OnToggleMode -> toggleMode()
            is CharacterEvent.OnFieldChanged -> updateField(event.key, event.text)
        }
    }
}
