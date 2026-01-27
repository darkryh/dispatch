package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType

object PreferencesMemory {

    val writingStyleConcept = Concept(
        keyword = "writing_style",
        description = """
            The user's preferred style for prose and narrative generation.
            Includes:
            - POV (First, Third Limited/Omniscient)
            - Tense (Past, Present)
            - Regional spelling (US, UK, Canadian)
            - Formatting habits (e.g., em-dashes vs hyphens)
            Use this to ensure generated text matches the user's voice.
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val collaborationModeConcept = Concept(
        keyword = "collaboration_mode",
        description = """
            How the user prefers to interact with the AI assistant.
            Examples:
            - "Coach": Ask probing questions, don't write the story.
            - "Co-writer": Draft scenes and propose concrete dialogue.
            - "Editor": Critique logic, grammar, and pacing.
            - "Architect": Focus on structure and world-building, not prose.
            Use this to adjust the agent's persona and initiative level.
        """.trimIndent(),
        factType = FactType.SINGLE,
    )

    val narrativePreferencesConcept = Concept(
        keyword = "narrative_preferences",
        description = """
            The user's creative likes, dislikes, and constraints.
            Includes:
            - Favorite tropes or genres (e.g., "Found Family", "Cyberpunk")
            - Disliked tropes (e.g., "Love Triangles")
            - Hard constraints (e.g., "No gore", "PG-13 only")
            Use this to guide creative suggestions and avoid unwanted content.
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val outputFormatConcept = Concept(
        keyword = "output_format",
        description = """
            Technical preferences for how the AI should structure its output.
            Examples:
            - "Always provide JSON for character sheets"
            - "Use markdown tables for timelines"
            - "Keep summaries under 50 words"
            Use this to format responses exactly as the user expects.
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val userConcepts = listOf(
        writingStyleConcept,
        collaborationModeConcept,
        narrativePreferencesConcept,
        outputFormatConcept,
    )
}
