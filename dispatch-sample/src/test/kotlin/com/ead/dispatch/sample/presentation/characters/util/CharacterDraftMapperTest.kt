package com.ead.dispatch.sample.presentation.characters.util

import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAIDraft
import com.ead.dispatch.sample.presentation.util.FieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterDraftMapperTest {
    @Test
    fun `applyDraft maps core fields`() {
        val draft = CharacterAIDraft(
            name = "Lina",
            description = "Stoic mentor",
            roles = listOf("mentor"),
            goal = "Protect the apprentice",
            traits = listOf("wise", "patient"),
            quirks = listOf("counts steps"),
            physical = CharacterAIDraft.PhysicalDraft(
                appearance = "tall and wiry",
                hair = "silver",
            ),
            summary = "A seasoned mentor with a quiet strength.",
        )

        val values = CharacterDraftMapper.applyDraft(draft, emptyMap())

        assertEquals("Lina", values[CharacterFieldKey.NAME]?.text)
        assertEquals("Stoic mentor", values[CharacterFieldKey.DESCRIPTION]?.text)
        assertEquals("mentor", values[CharacterFieldKey.ROLES]?.text)
        assertEquals("Protect the apprentice", values[CharacterFieldKey.GOAL]?.text)
        assertEquals("wise, patient", values[CharacterFieldKey.TRAITS]?.text)
        assertEquals("counts steps", values[CharacterFieldKey.QUIRKS]?.text)
        assertEquals("tall and wiry", values[CharacterFieldKey.APPEARANCE]?.text)
        assertEquals("silver", values[CharacterFieldKey.HAIR]?.text)
    }

    @Test
    fun `applyDraft preserves existing when draft is blank`() {
        val draft = CharacterAIDraft(name = "")
        val current = mapOf(CharacterFieldKey.NAME to FieldValue("Existing"))

        val values = CharacterDraftMapper.applyDraft(draft, current)

        assertEquals("Existing", values[CharacterFieldKey.NAME]?.text)
    }
}
