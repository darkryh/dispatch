package com.ead.dispatch.sample.data.db.entities

data class StoryCharacterRecord(
    /** Stable identifier for the character record. */
    val id: String,
    /** Parent story id that owns this character. */
    val storyId: String,
    /** Character name used in prompts and UI. */
    val name: String,
    /** Short description for quick recall. */
    val description: String?,
    /** Descriptive traits for grounding (normalized list). */
    val traits: List<String> = emptyList(),
    /** Roles in the story for AI guidance (normalized list). */
    val roles: List<String> = emptyList(),
    /** Primary goal used for motivation checks. */
    val goal: String? = null,
    /** Underlying motivation for behavior. */
    val motivation: String? = null,
    /** Character flaw for conflict tracking. */
    val flaw: String? = null,
    /** Temperament tag for voice consistency. */
    val temperament: String? = null,
    /** Age text for continuity. */
    val age: String? = null,
    /** Pronouns for consistent references. */
    val pronouns: String? = null,
    /** Occupation or role label. */
    val occupation: String? = null,
    /** Backstory summary for AI grounding. */
    val backstory: String? = null,
    /** Voice/style notes for dialogue. */
    val voice: String? = null,
    /** Internal conflict summary. */
    val internalConflict: String? = null,
    /** Small quirks for flavor (normalized list). */
    val quirks: List<String> = emptyList(),
    /** Physical profile data grouped for readability. */
    val physical: PhysicalProfile? = null,
    /** Creation time for audits. */
    val createdAt: Long,
) {
    data class PhysicalProfile(
        /** Short physical appearance summary. */
        val appearance: String? = null,
        /** Height descriptor. */
        val height: String? = null,
        /** Build/body type descriptor. */
        val build: String? = null,
        /** Hair descriptor. */
        val hair: String? = null,
        /** Eye descriptor. */
        val eyes: String? = null,
        /** Skin tone descriptor. */
        val skinTone: String? = null,
        /** Notable scars, tattoos, or marks. */
        val distinguishingMarks: String? = null,
        /** Clothing or style notes. */
        val styleNotes: String? = null,
    )
}
