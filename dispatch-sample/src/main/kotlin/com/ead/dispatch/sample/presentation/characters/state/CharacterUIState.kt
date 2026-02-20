package com.ead.dispatch.sample.presentation.characters.state

import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey
import com.ead.dispatch.sample.presentation.characters.util.CharacterUIMode
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAIDraft
import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAIMode

data class CharacterUIState(
    val mode: CharacterUIMode = CharacterUIMode.MANUAL,
    val values: Map<CharacterFieldKey, FieldValue> = emptyMap(),
    val storyId: String? = null,
    val characterId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val status: String? = null,
    val confirmDelete: Boolean = false,
    val isGenerating: Boolean = false,
    val aiDraft: CharacterAIDraft? = null,
    val aiError: String? = null,
    val aiMode: CharacterAIMode = CharacterAIMode.CREATIVE,
)
