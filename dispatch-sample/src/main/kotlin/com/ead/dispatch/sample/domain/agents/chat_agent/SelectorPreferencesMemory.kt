package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType

object SelectorPreferencesMemory {

    private val globalContract = """
        Extraction contract (must follow all):
        - Extract only reusable direction preferences revealed by selector choices.
        - Do not store story-specific details, names, plot facts, or lore.
        - Store short normalized labels (2-8 words), not explanations.
        - If no stable preference is expressed, return no facts.
        - Extract at most 3 facts per concept per turn.
    """.trimIndent()

    val selectorNamingDirectionPreference = Concept(
        keyword = "selector_naming_direction_preference",
        description = """
            Preferred naming/title direction selected by the user in choice prompts.
            Examples:
            - character-focused titles
            - mystery-focused titles
            - atmospheric titles
            Allowed scope:
            - Durable naming/title direction only.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val selectorCharacterDirectionPreference = Concept(
        keyword = "selector_character_direction_preference",
        description = """
            Preferred character design direction selected by the user.
            Examples:
            - morally gray protagonist
            - vulnerable antihero
            - mentor-led arc
            Allowed scope:
            - Reusable character direction taste, not specific bios.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val selectorPlotDirectionPreference = Concept(
        keyword = "selector_plot_direction_preference",
        description = """
            Preferred plot direction selected by the user.
            Examples:
            - mystery-forward pacing
            - conflict escalation
            - investigation-first reveals
            Allowed scope:
            - Reusable plot direction taste, not concrete events.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val selectorToneDirectionPreference = Concept(
        keyword = "selector_tone_direction_preference",
        description = """
            Preferred tone or atmosphere direction selected by the user.
            Examples:
            - dark atmospheric
            - hopeful recovery arc
            - restrained violence tone
            Allowed scope:
            - Reusable tone direction taste only.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val selectorGeneralCreativePreference = Concept(
        keyword = "selector_general_creative_preference",
        description = """
            General reusable creative direction preference from selector choices.
            Use only when no specific family concept applies.
            Examples:
            - prefers grounded options
            - prefers high-tension direction
            Allowed scope:
            - Cross-session creative direction preference only.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val userConcepts = listOf(
        selectorNamingDirectionPreference,
        selectorCharacterDirectionPreference,
        selectorPlotDirectionPreference,
        selectorToneDirectionPreference,
        selectorGeneralCreativePreference,
    )
}
