package com.ead.dispatch.sample.domain.embedding

import com.ead.dispatch.sample.data.db.entities.*

object EmbeddingTextBuilder {
    fun story(record: StoryRecord): String = """
        [Story: ${labelValue(record.title)}]
        Title: ${value(record.title)}
        Genre: ${value(record.genre)}
        Setting: ${value(record.setting)}
        Plot: ${value(record.plotOutline)}
        Status: ${value(record.status?.name)}
        Logline: ${value(record.styleProfile?.logline)}
        Theme: ${value(record.styleProfile?.theme)}
        Tone: ${value(record.styleProfile?.tone)}
        Stakes: ${value(record.styleProfile?.stakes)}
        POV: ${value(record.styleProfile?.pov)}
        Tense: ${value(record.styleProfile?.tense)}
        Audience: ${value(record.styleProfile?.targetAudience)}
        Pacing: ${value(record.styleProfile?.pacing)}
        Style refs: ${listValue(record.styleRefs)}
        Emotional beats: ${listValue(record.emotionalBeats)}
    """.trimIndent().trim()

    fun character(record: StoryCharacterRecord): String = """
        [Character: ${labelValue(record.name)}]
        Name: ${value(record.name)}
        Description: ${value(record.description)}
        Traits: ${listValue(record.traits)}
        Roles: ${listValue(record.roles)}
        Goal: ${value(record.goal)}
        Motivation: ${value(record.motivation)}
        Flaw: ${value(record.flaw)}
        Temperament: ${value(record.temperament)}
        Age: ${value(record.age)}
        Pronouns: ${value(record.pronouns)}
        Occupation: ${value(record.occupation)}
        Backstory: ${value(record.backstory)}
        Voice: ${value(record.voice)}
        Internal conflict: ${value(record.internalConflict)}
        Quirks: ${listValue(record.quirks)}
        Appearance: ${value(record.physical?.appearance)}
        Height: ${value(record.physical?.height)}
        Build: ${value(record.physical?.build)}
        Hair: ${value(record.physical?.hair)}
        Eyes: ${value(record.physical?.eyes)}
        Skin tone: ${value(record.physical?.skinTone)}
        Marks: ${value(record.physical?.distinguishingMarks)}
        Style notes: ${value(record.physical?.styleNotes)}
    """.trimIndent().trim()

    fun location(record: StoryLocationRecord): String = """
        [Location: ${labelValue(record.profile.name)}]
        Name: ${value(record.profile.name)}
        Description: ${value(record.profile.description)}
        Tags: ${listValue(record.tags)}
    """.trimIndent().trim()

    fun arc(record: StoryArcRecord): String = """
        [Arc: ${labelValue(record.title)}]
        Title: ${value(record.title)}
        Summary: ${value(record.summary)}
        Scope: ${value(record.scopeType.name)}
        Scope ID: ${value(record.scopeId)}
        Status: ${value(record.status?.name)}
    """.trimIndent().trim()

    fun event(record: StoryEventRecord): String = """
        [Event: ${labelValue(record.name)}]
        Name: ${value(record.name)}
        Description: ${value(record.description)}
    """.trimIndent().trim()

    fun timelineEntry(record: StoryTimelineEntryRecord): String = """
        [Timeline: ${labelValue(record.title)}]
        Title: ${value(record.title)}
        Description: ${value(record.description)}
        Order: ${record.orderIndex}
    """.trimIndent().trim()

    fun worldRule(record: StoryWorldRuleRecord): String = """
        [World Rule: ${labelValue(record.title)}]
        Title: ${value(record.title)}
        Description: ${value(record.description)}
    """.trimIndent().trim()

    fun culture(record: StoryCultureRecord): String = """
        [Culture: ${labelValue(record.name)}]
        Name: ${value(record.name)}
        Description: ${value(record.description)}
    """.trimIndent().trim()

    fun organization(record: StoryOrganizationRecord): String = """
        [Organization: ${labelValue(record.name)}]
        Name: ${value(record.name)}
        Description: ${value(record.description)}
    """.trimIndent().trim()

    fun artifact(record: StoryArtifactRecord): String = """
        [Artifact: ${labelValue(record.name)}]
        Name: ${value(record.name)}
        Description: ${value(record.description)}
        Owner ID: ${value(record.ownerId)}
        Owner Type: ${value(record.ownerType)}
        Location ID: ${value(record.locationId)}
    """.trimIndent().trim()

    fun relationship(record: StoryRelationshipRecord): String = """
        [Relationship: ${labelValue(record.relation)}]
        Subject: ${value("${record.subjectType}:${record.subjectId}")}
        Object: ${value("${record.objectType}:${record.objectId}")}
        Relation: ${value(record.relation)}
        Notes: ${value(record.notes)}
    """.trimIndent().trim()

    fun locationFeature(record: StoryLocationFeatureRecord): String = """
        [Location Feature: ${labelValue(record.name)}]
        Name: ${value(record.name)}
        Description: ${value(record.description)}
        Location ID: ${value(record.locationId)}
    """.trimIndent().trim()

    private fun value(input: String?): String =
        input?.trim()?.takeIf { it.isNotEmpty() } ?: "—"

    private fun listValue(values: List<String>): String =
        values.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.joinToString(", ").ifBlank { "—" }

    private fun labelValue(input: String?): String =
        input?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown"
}
