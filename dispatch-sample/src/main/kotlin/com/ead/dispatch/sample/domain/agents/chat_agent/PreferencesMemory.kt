package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType

object PreferencesMemory {

    private val globalContract = """
        Extraction contract (must follow all):
        - Evidence rule: extract only when the user explicitly states a reusable preference.
        - No-inference rule: do not infer preferences from story/character/plot/world details.
        - Output shape: save short normalized values (2-6 words), not explanations.
        - Unknown rule: if no valid preference is present, return no facts.
        - Volume rule: extract at most 3 facts for this concept per turn.
        Forbidden in all cases:
        - Names, story titles, settings, events, timelines, character bios, lore.
        - One-task instructions that do not apply across sessions.
    """.trimIndent()

    val writerPovPreferenceConcept = Concept(
        keyword = "writer_pov_preference",
        description = """
            Writer preference for narrative point of view (POV).
            Examples:
            - First person.
            - Third-person limited.
            - Third-person omniscient.
            - Mixed POV.
            Sample value patterns (examples, not strict enums):
            - first_person
            - third_limited
            - third_omniscient
            - mixed_pov
            Allowed scope:
            - Only global POV preference and POV mixing preference.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val writerTensePreferenceConcept = Concept(
        keyword = "writer_tense_preference",
        description = """
            Writer preference for narrative tense.
            Examples:
            - Present tense.
            - Past tense.
            - Present for main timeline with past flashbacks.
            Sample value patterns (examples, not strict enums):
            - present
            - past
            - mixed_tense
            Allowed scope:
            - Only global tense preference and tense-mixing preference.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val writerToneLikePreferenceConcept = Concept(
        keyword = "writer_tone_like_preference",
        description = """
            Writer preferred tones (liked).
            Examples:
            - Atmospheric, melancholic, hopeful, gritty, whimsical.
            - Psychological, suspenseful, intimate, epic.
            Sample value patterns (examples, not strict enums):
            - atmospheric
            - melancholic
            - dark
            - hopeful
            - gritty
            - psychological
            - suspenseful
            Allowed scope:
            - Only tones the writer prefers repeatedly across stories.
            - Do not store avoided tones here.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val writerProseStylePreferenceConcept = Concept(
        keyword = "writer_prose_style_preference",
        description = """
            Writer preference for prose texture and sentence style.
            Examples:
            - Minimalist prose vs lyrical prose.
            - Short punchy sentences vs flowing descriptive sentences.
            - Dense imagery vs clean direct language.
            Sample value patterns (examples, not strict enums):
            - minimalist
            - lyrical
            - direct
            - descriptive
            - introspective
            Allowed scope:
            - Global prose texture, diction density, and sentence style preferences.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    val writerContentBoundaryPreferenceConcept = Concept(
        keyword = "writer_content_boundary_preference",
        description = """
            Writer preference for content boundaries and sensitivity limits.
            Examples:
            - No explicit gore.
            - Keep romance mild.
            - Target PG-13 intensity.
            Sample value patterns (examples, not strict enums):
            - no_explicit_gore
            - pg_13
            - limited_romance
            - no_sexual_content
            - limited_profanity
            Allowed scope:
            - Stable boundaries for explicitness, violence, intimacy, and language.
            - Do not store one-scene safety notes here.
            $globalContract
        """.trimIndent(),
        factType = FactType.MULTIPLE,
    )

    // Core writer-preference memory only (top 5).
    val userConcepts = listOf(
        writerPovPreferenceConcept,
        writerTensePreferenceConcept,
        writerToneLikePreferenceConcept,
        writerProseStylePreferenceConcept,
        writerContentBoundaryPreferenceConcept,
    )
}
