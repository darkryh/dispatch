package com.ead.dispatch.sample.presentation.characters.util

import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAIDraft
import com.ead.dispatch.sample.presentation.util.FieldValue

object CharacterDraftMapper {
    fun applyDraft(
        draft: CharacterAIDraft,
        current: Map<CharacterFieldKey, FieldValue>,
    ): Map<CharacterFieldKey, FieldValue> {
        val updated = current.toMutableMap()

        fun set(key: CharacterFieldKey, value: String?) {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isNotBlank()) {
                updated[key] = FieldValue(trimmed)
            }
        }

        fun setList(key: CharacterFieldKey, values: List<String>) {
            val filtered = values.map { it.trim() }.filter { it.isNotBlank() }
            if (filtered.isNotEmpty()) {
                updated[key] = FieldValue(filtered.joinToString(", "))
            }
        }

        set(CharacterFieldKey.NAME, draft.name)
        set(CharacterFieldKey.DESCRIPTION, draft.description)
        setList(CharacterFieldKey.ROLES, draft.roles)
        set(CharacterFieldKey.GOAL, draft.goal)
        set(CharacterFieldKey.MOTIVATION, draft.motivation)
        set(CharacterFieldKey.FLAW, draft.flaw)
        set(CharacterFieldKey.INTERNAL_CONFLICT, draft.internalConflict)
        set(CharacterFieldKey.TEMPERAMENT, draft.temperament)
        set(CharacterFieldKey.AGE, draft.age)
        set(CharacterFieldKey.PRONOUNS, draft.pronouns)
        set(CharacterFieldKey.OCCUPATION, draft.occupation)
        set(CharacterFieldKey.BACKSTORY, draft.backstory)
        set(CharacterFieldKey.VOICE, draft.voice)
        setList(CharacterFieldKey.TRAITS, draft.traits)
        setList(CharacterFieldKey.QUIRKS, draft.quirks)

        val physical = draft.physical
        if (physical != null) {
            set(CharacterFieldKey.APPEARANCE, physical.appearance)
            set(CharacterFieldKey.HEIGHT, physical.height)
            set(CharacterFieldKey.BUILD, physical.build)
            set(CharacterFieldKey.HAIR, physical.hair)
            set(CharacterFieldKey.EYES, physical.eyes)
            set(CharacterFieldKey.SKIN_TONE, physical.skinTone)
            set(CharacterFieldKey.DISTINGUISHING_MARKS, physical.distinguishingMarks)
            set(CharacterFieldKey.STYLE_NOTES, physical.styleNotes)
        }

        return updated
    }
}
