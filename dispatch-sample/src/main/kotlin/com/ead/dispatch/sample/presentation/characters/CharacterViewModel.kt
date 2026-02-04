package com.ead.dispatch.sample.presentation.characters

import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterAgent
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterAIRequest
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterAIMode
import com.ead.dispatch.sample.domain.agents.character_agent.CharacterStoryContext
import com.ead.dispatch.sample.navigation.CharacterRoute
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.sample.presentation.characters.event.CharacterEvent
import com.ead.dispatch.sample.presentation.characters.state.CharacterUIState
import com.ead.dispatch.sample.presentation.characters.util.CharacterDraftMapper
import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey
import com.ead.dispatch.sample.presentation.characters.util.CharacterUIMode
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class CharacterViewModel(
    private val repository: StructuredIndexRepository,
    private val characterAgent: CharacterAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CharacterRoute>()

    private val _uiState = MutableStateFlow(
        CharacterUIState(
            storyId = route.storyId,
            characterId = route.characterId,
            isLoading = route.characterId != null,
        )
    )
    val uiState: StateFlow<CharacterUIState> = _uiState.asStateFlow()

    init {
        val storyId = route.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No session id provided.") }
        } else {
            val characterId = route.characterId?.trim().takeIf { !it.isNullOrEmpty() }
            if (characterId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val character = repository.getStoryCharacters(storyId)
                            .firstOrNull { it.id == characterId }

                        if (character == null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Character not found."
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    values = valuesFromRecord(character),
                                )
                            }
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load character."
                            )
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: CharacterEvent) {
        when (event) {
            is CharacterEvent.OnToggleMode -> toggleMode()
            is CharacterEvent.OnFieldChanged -> updateField(event.key, event.text)
            is CharacterEvent.OnGenerateAiDraft -> generateAiDraft(regenerate = false)
            is CharacterEvent.OnRegenerateAiDraft -> generateAiDraft(regenerate = true)
            is CharacterEvent.OnApplyAiDraft -> applyAiDraft()
            is CharacterEvent.OnDiscardAiDraft -> discardAiDraft()
            is CharacterEvent.OnToggleAiMode -> toggleAiMode()
            is CharacterEvent.OnSave -> saveCharacter()
            is CharacterEvent.OnRequestDelete -> requestDelete()
            is CharacterEvent.OnConfirmDelete -> confirmDelete()
            is CharacterEvent.OnCancelDelete -> cancelDelete()
        }
    }


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
            val resetDraft = state.mode == CharacterUIMode.AUTOMATIC &&
                (key == CharacterFieldKey.PROMPT || key == CharacterFieldKey.CONSTRAINTS)
            state.copy(
                values = state.values + (key to current.copy(text = text)),
                aiDraft = if (resetDraft) null else state.aiDraft,
                aiError = if (resetDraft) null else state.aiError,
            )
        }
    }

    private fun generateAiDraft(regenerate: Boolean) {
        val state = _uiState.value
        if (state.mode != CharacterUIMode.AUTOMATIC) return

        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }

        val prompt = state.values[CharacterFieldKey.PROMPT]?.text?.trim().orEmpty()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(status = "Prompt is required for AI generation.") }
            return
        }

        val constraints = state.values[CharacterFieldKey.CONSTRAINTS]?.text?.trim()?.takeIf { it.isNotEmpty() }

        _uiState.update {
            it.copy(
                isGenerating = true,
                aiError = null,
                status = if (regenerate) "Regenerating character draft..." else "Generating character draft...",
                aiDraft = if (regenerate) null else it.aiDraft,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val story = repository.getStoryById(storyId)
                val existingNames = repository.getStoryCharacters(storyId)
                    .map { it.name }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val request = CharacterAIRequest(
                    storyId = storyId,
                    prompt = prompt,
                    constraints = constraints,
                    story = CharacterStoryContext.fromStory(story),
                    existingCharacterNames = existingNames,
                    mode = state.aiMode,
                )

                characterAgent.generateDraft(request)
            }.onSuccess { draft ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        aiDraft = draft,
                        aiError = null,
                        status = "Draft ready. Apply to edit.",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        aiDraft = null,
                        aiError = error.message ?: "AI generation failed.",
                        status = "AI generation failed.",
                    )
                }
            }
        }
    }

    private fun applyAiDraft() {
        val state = _uiState.value
        val draft = state.aiDraft
        if (draft == null) {
            _uiState.update { it.copy(status = "Generate a draft before applying.") }
            return
        }

        val updatedValues = CharacterDraftMapper.applyDraft(draft, state.values)
        _uiState.update {
            it.copy(
                values = updatedValues,
                aiDraft = null,
                aiError = null,
                mode = CharacterUIMode.MANUAL,
                status = "Draft applied. Review and save.",
            )
        }
    }

    private fun discardAiDraft() {
        _uiState.update { it.copy(aiDraft = null, aiError = null, status = "Draft discarded.") }
    }

    private fun toggleAiMode() {
        val next = if (_uiState.value.aiMode == CharacterAIMode.NORMAL) {
            CharacterAIMode.CREATIVE
        } else {
            CharacterAIMode.NORMAL
        }
        _uiState.update {
            it.copy(
                aiMode = next,
                aiDraft = null,
                aiError = null,
                status = "AI mode: ${next.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }}.",
            )
        }
    }

    private fun requestDelete() {
        val currentId = _uiState.value.characterId?.trim().takeIf { !it.isNullOrEmpty() }
        if (currentId == null) {
            _uiState.update { it.copy(status = "Save the character before deleting.") }
            return
        }
        _uiState.update { it.copy(confirmDelete = true, status = null) }
    }

    private fun cancelDelete() {
        _uiState.update { it.copy(confirmDelete = false) }
    }

    private fun confirmDelete() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        val characterId = state.characterId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null || characterId == null) {
            _uiState.update { it.copy(confirmDelete = false, status = "Cannot delete: missing ids.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteStoryCharacter(characterId)
                _uiState.update { it.copy(confirmDelete = false, status = "Character deleted.") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(confirmDelete = false, status = error.message ?: "Delete failed.")
                }
            }
        }
    }

    private fun saveCharacter() {
        val state = _uiState.value
        val storyId = state.storyId?.trim().takeIf { !it.isNullOrEmpty() }
        if (storyId == null) {
            _uiState.update { it.copy(status = "Missing session id.") }
            return
        }

        val name = state.values[CharacterFieldKey.NAME]?.text?.trim().orEmpty()
        if (name.isBlank()) {
            _uiState.update { it.copy(status = "Name is required.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val existing = state.characterId?.let { id ->
                    repository.getStoryCharacters(storyId).firstOrNull { it.id == id }
                }

                val record = toRecord(
                    storyId = storyId,
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    createdAt = existing?.createdAt ?: now,
                    values = state.values,
                )

                if (existing == null) {
                    repository.insertStoryCharacter(record)
                    _uiState.update { it.copy(characterId = record.id, status = "Character created.") }
                } else {
                    repository.updateStoryCharacter(record)
                    _uiState.update { it.copy(status = "Character updated.") }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(status = error.message ?: "Save failed.") }
            }
        }
    }

    private fun valuesFromRecord(character: StoryCharacterRecord): Map<CharacterFieldKey, FieldValue> {
        val physical = character.physical

        return mapOf(
            CharacterFieldKey.NAME to FieldValue(character.name),
            CharacterFieldKey.DESCRIPTION to FieldValue(character.description.orEmpty()),
            CharacterFieldKey.ROLES to FieldValue(character.roles.joinToString()),
            CharacterFieldKey.GOAL to FieldValue(character.goal.orEmpty()),
            CharacterFieldKey.MOTIVATION to FieldValue(character.motivation.orEmpty()),
            CharacterFieldKey.FLAW to FieldValue(character.flaw.orEmpty()),
            CharacterFieldKey.INTERNAL_CONFLICT to FieldValue(character.internalConflict.orEmpty()),
            CharacterFieldKey.TEMPERAMENT to FieldValue(character.temperament.orEmpty()),
            CharacterFieldKey.AGE to FieldValue(character.age.orEmpty()),
            CharacterFieldKey.PRONOUNS to FieldValue(character.pronouns.orEmpty()),
            CharacterFieldKey.OCCUPATION to FieldValue(character.occupation.orEmpty()),
            CharacterFieldKey.BACKSTORY to FieldValue(character.backstory.orEmpty()),
            CharacterFieldKey.VOICE to FieldValue(character.voice.orEmpty()),
            CharacterFieldKey.TRAITS to FieldValue(character.traits.joinToString()),
            CharacterFieldKey.QUIRKS to FieldValue(character.quirks.joinToString()),
            CharacterFieldKey.APPEARANCE to FieldValue(physical?.appearance.orEmpty()),
            CharacterFieldKey.HEIGHT to FieldValue(physical?.height.orEmpty()),
            CharacterFieldKey.BUILD to FieldValue(physical?.build.orEmpty()),
            CharacterFieldKey.HAIR to FieldValue(physical?.hair.orEmpty()),
            CharacterFieldKey.EYES to FieldValue(physical?.eyes.orEmpty()),
            CharacterFieldKey.SKIN_TONE to FieldValue(physical?.skinTone.orEmpty()),
            CharacterFieldKey.DISTINGUISHING_MARKS to FieldValue(physical?.distinguishingMarks.orEmpty()),
            CharacterFieldKey.STYLE_NOTES to FieldValue(physical?.styleNotes.orEmpty()),
        )
    }

    private fun toRecord(
        storyId: String,
        id: String,
        createdAt: Long,
        values: Map<CharacterFieldKey, FieldValue>,
    ): StoryCharacterRecord {

        val text = { key: CharacterFieldKey -> values[key]?.text?.trim().orEmpty() }

        val list = { key: CharacterFieldKey ->
            text(key).split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        val physical = StoryCharacterRecord.PhysicalProfile(
            appearance = text(CharacterFieldKey.APPEARANCE).ifBlank { null },
            height = text(CharacterFieldKey.HEIGHT).ifBlank { null },
            build = text(CharacterFieldKey.BUILD).ifBlank { null },
            hair = text(CharacterFieldKey.HAIR).ifBlank { null },
            eyes = text(CharacterFieldKey.EYES).ifBlank { null },
            skinTone = text(CharacterFieldKey.SKIN_TONE).ifBlank { null },
            distinguishingMarks = text(CharacterFieldKey.DISTINGUISHING_MARKS).ifBlank { null },
            styleNotes = text(CharacterFieldKey.STYLE_NOTES).ifBlank { null },
        )

        return StoryCharacterRecord(
            id = id,
            storyId = storyId,
            name = text(CharacterFieldKey.NAME),
            description = text(CharacterFieldKey.DESCRIPTION).ifBlank { null },
            traits = list(CharacterFieldKey.TRAITS),
            roles = list(CharacterFieldKey.ROLES),
            goal = text(CharacterFieldKey.GOAL).ifBlank { null },
            motivation = text(CharacterFieldKey.MOTIVATION).ifBlank { null },
            flaw = text(CharacterFieldKey.FLAW).ifBlank { null },
            temperament = text(CharacterFieldKey.TEMPERAMENT).ifBlank { null },
            age = text(CharacterFieldKey.AGE).ifBlank { null },
            pronouns = text(CharacterFieldKey.PRONOUNS).ifBlank { null },
            occupation = text(CharacterFieldKey.OCCUPATION).ifBlank { null },
            backstory = text(CharacterFieldKey.BACKSTORY).ifBlank { null },
            voice = text(CharacterFieldKey.VOICE).ifBlank { null },
            internalConflict = text(CharacterFieldKey.INTERNAL_CONFLICT).ifBlank { null },
            quirks = list(CharacterFieldKey.QUIRKS),
            physical = physical,
            createdAt = createdAt,
        )
    }
}
