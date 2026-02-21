package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType

object SelectorPreferencesMemory {

    private val globalContract = """
        Extraction contract (must follow all):
        - Evidence rule: extract only reusable chat readability preference signals.
        - No-story-detail rule: do not store names, entities, lore, plot facts, or one-story specifics.
        - Output shape: save one normalized label (2-4 words), not explanations.
        - Stability rule: if preference is unclear or one-off, return no facts.
        - Volume rule: extract at most 1 fact for this concept per turn.
        Forbidden in all cases:
        - Story direction details, archetypes, worldbuilding specifics, or scene-level content.
        - Behavioral control rules (when to ask, when to execute tools, confirmation policy).
    """.trimIndent()

    val chatReadabilityPreference = Concept(
        keyword = "chat_readability_preference",
        description = """
            Preferred readability style for chat-mode assistant responses.
            Examples:
            - compact clarity
            - balanced clarity
            - rich clarity
            Sample value patterns (examples, not strict enums):
            - compact_clarity
            - balanced_clarity
            - rich_clarity
            Allowed scope:
            - Reusable comfort preference for how responses are phrased and packaged in chat mode.
            $globalContract
        """.trimIndent(),
        factType = FactType.SINGLE,
    )

    val userConcepts = listOf(
        chatReadabilityPreference,
    )
}
