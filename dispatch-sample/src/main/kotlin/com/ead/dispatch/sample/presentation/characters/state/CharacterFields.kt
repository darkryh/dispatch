package com.ead.dispatch.sample.presentation.characters.state

import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldDefinition
import com.ead.dispatch.sample.presentation.characters.util.CharacterFieldKey

object CharacterFields {
    val manual: List<CharacterFieldDefinition> = listOf(
        CharacterFieldDefinition(
            CharacterFieldKey.NAME,
            "Name",
            placeholder = "Character name (e.g., John Doe)"
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.DESCRIPTION,
            "Description (Optional)",
            maxLines = 3,
            placeholder = "Short summary for quick recall (e.g., quiet cartographer with a limp)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.ROLES,
            "Roles",
            placeholder = "Story roles, comma-separated (e.g., protagonist, rival)",
            helper = "Comma-separated roles used by the AI.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.GOAL,
            "Goal (Optional)",
            maxLines = 2,
            placeholder = "What they want most (e.g., find her missing brother)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.MOTIVATION,
            "Motivation (Optional)",
            maxLines = 2,
            placeholder = "Why they want it (e.g., guilt over leaving him behind)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.FLAW,
            "Flaw (Optional)",
            maxLines = 2,
            placeholder = "Trait that causes problems (e.g., trusts strangers too fast)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.INTERNAL_CONFLICT,
            "Internal Conflict (Optional)",
            maxLines = 2,
            placeholder = "Competing desires or fears (e.g., wants freedom but fears loneliness)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.TEMPERAMENT,
            "Temperament (Optional)",
            placeholder = "Overall temperament, comma-separated (e.g., calm, stubborn)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.AGE,
            "Age (Optional)",
            placeholder = "Age or range (e.g., 29 / late 20s)"
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.PRONOUNS,
            "Pronouns (Optional)",
            placeholder = "Pronouns (e.g., she/her)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.OCCUPATION,
            "Occupation (Optional)",
            placeholder = "Primary occupation (e.g., pilot, archivist)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.BACKSTORY,
            "Backstory (Optional)",
            maxLines = 4,
            placeholder = "Key background details (e.g., raised on a cargo ship)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.VOICE,
            "Voice (Optional)",
            maxLines = 2,
            placeholder = "Speech style notes (e.g., short sentences, dry humor)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.TRAITS,
            "Traits (Optional)",
            maxLines = 3,
            placeholder = "Personality traits, comma-separated (e.g., brave, observant)",
            helper = "Comma-separated traits or adjectives.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.QUIRKS,
            "Quirks (Optional)",
            maxLines = 2,
            placeholder = "Habits/quirks, comma-separated (e.g., taps a ring)",
            helper = "Comma-separated quirks or habits.",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.APPEARANCE,
            "Appearance (Optional)",
            maxLines = 3,
            placeholder = "Overall appearance (e.g., tall, wiry, silver hair)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.HEIGHT,
            "Height (Optional)",
            placeholder = "Height (e.g., 6'1\" / 185 cm)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.BUILD,
            "Build (Optional)",
            placeholder = "Body build (e.g., lean, athletic)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.HAIR,
            "Hair (Optional)",
            placeholder = "Hair color/style (e.g., black, shoulder-length)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.EYES,
            "Eyes (Optional)",
            placeholder = "Eye color/traits (e.g., green, sharp gaze)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.SKIN_TONE,
            "Skin Tone (Optional)",
            placeholder = "Skin tone (e.g., olive, freckled)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.DISTINGUISHING_MARKS,
            "Distinguishing Marks (Optional)",
            maxLines = 2,
            placeholder = "Scars/tattoos (e.g., scar across left eyebrow)",
        ),
        CharacterFieldDefinition(
            CharacterFieldKey.STYLE_NOTES,
            "Style Notes (Optional)",
            maxLines = 2,
            placeholder = "Clothing/style signature (e.g., worn leather coat)",
        ),
    )


    val automatic: List<CharacterFieldDefinition> = listOf(
        CharacterFieldDefinition(
            CharacterFieldKey.PROMPT,
            "Prompt",
            maxLines = 6,
            placeholder = "Describe the character (e.g., a stoic healer from the coast)",
        ),
    )
}
