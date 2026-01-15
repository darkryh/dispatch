package com.ead.dispatch.sample.presentation.characters.state

import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldDefinition
import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey

object CharacterFields {
    val manual: List<CharacterFieldDefinition> = listOf(
        CharacterFieldDefinition(
            CharacterFieldKey.NAME,
            "Name",
            placeholder = "e.g., Jane Doe"
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.DESCRIPTION,
            "Description (Optional)",
            maxLines = 3,
            placeholder = "e.g., a quiet cartographer with a sharp memory and a limp",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.ROLES,
            "Roles",
            placeholder = "e.g., protagonist, navigator, rival",
            helper = "Comma-separated roles used by the AI.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.GOAL,
            "Goal (Optional)",
            maxLines = 2,
            placeholder = "e.g., find her missing brother before winter ends",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.MOTIVATION,
            "Motivation (Optional)",
            maxLines = 2,
            placeholder = "e.g., guilt over leaving him behind",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.FLAW,
            "Flaw (Optional)",
            maxLines = 2,
            placeholder = "e.g., trusts strangers too quickly",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.INTERNAL_CONFLICT,
            "Internal Conflict (Optional)",
            maxLines = 2,
            placeholder = "e.g., wants freedom but fears being alone",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.TEMPERAMENT,
            "Temperament (Optional)",
            placeholder = "e.g., calm, curious, stubborn",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.AGE,
            "Age (Optional)",
            placeholder = "e.g., 29"
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.PRONOUNS,
            "Pronouns (Optional)",
            placeholder = "e.g., she/her",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.OCCUPATION,
            "Occupation (Optional)",
            placeholder = "e.g., pilot, mechanic, archivist",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.BACKSTORY,
            "Backstory (Optional)",
            maxLines = 4,
            placeholder = "e.g., raised on a cargo ship after a flood destroyed her town",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.VOICE,
            "Voice (Optional)",
            maxLines = 2,
            placeholder = "e.g., short sentences, avoids contractions, dry humor",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.TRAITS,
            "Traits (Optional)",
            maxLines = 3,
            placeholder = "e.g., brave, observant, quick learner",
            helper = "Comma-separated traits or adjectives.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.QUIRKS,
            "Quirks (Optional)",
            maxLines = 2,
            placeholder = "e.g., taps a ring, collects old tickets",
            helper = "Comma-separated quirks or habits.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.APPEARANCE,
            "Appearance (Optional)",
            maxLines = 3,
            placeholder = "e.g., tall, wiry, silver hair, sun-worn skin",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.HEIGHT,
            "Height (Optional)",
            placeholder = "e.g., 6'1\" / 185 cm",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.BUILD,
            "Build (Optional)",
            placeholder = "e.g., lean, athletic, broad-shouldered",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.HAIR,
            "Hair (Optional)",
            placeholder = "e.g., black, shoulder-length, wavy",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.EYES,
            "Eyes (Optional)",
            placeholder = "e.g., green, sharp gaze",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.SKIN_TONE,
            "Skin Tone (Optional)",
            placeholder = "e.g., olive, freckled",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.DISTINGUISHING_MARKS,
            "Distinguishing Marks (Optional)",
            maxLines = 2,
            placeholder = "e.g., scar across left eyebrow, small tattoo on wrist",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.STYLE_NOTES,
            "Style Notes (Optional)",
            maxLines = 2,
            placeholder = "e.g., worn leather coat, always carries a satchel",
        ),
    )


    val automatic: List<CharacterFieldDefinition> = listOf(
        CharacterFieldDefinition(
            CharacterFieldKey.PROMPT,
            "Prompt",
            maxLines = 6,
            placeholder = "Describe the character you want to generate.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.CONSTRAINTS,
            "Constraints",
            maxLines = 4,
            placeholder = "Add rules or boundaries for the AI output.",
        ),
    )
}
