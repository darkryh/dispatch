package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.ContentStatus
import kotlinx.serialization.Serializable

@Serializable
data class StoryRecord(
    /** Stable identifier for the story record. */
    val id: String,
    /** Owning session id for chat/story mode linkage. */
    val sessionId: String,
    /** Story title shown in menus and exports. */
    val title: String?,
    /** Genre tag used for prompts and filters. */
    val genre: String?,
    /** Setting description for planning context. */
    val setting: String?,
    /** High-level plot outline used in chat mode. */
    val plotOutline: String?,
    /** Draft status for the whole story. */
    val status: ContentStatus? = null,
    /** Narrative style profile grouped for clarity. */
    val styleProfile: StoryStyleProfile? = null,
    /** External style references used by AI (normalized list). */
    val styleRefs: List<String> = emptyList(),
    /** Emotional beats list for planning (normalized list). */
    val emotionalBeats: List<String> = emptyList(),
    /** Creation time for ordering and audits. */
    val createdAt: Long,
    /** Last update time for metadata changes. */
    val updatedAt: Long,
) {
    @Serializable
    data class StoryStyleProfile(
        /** One-line logline for quick summaries. */
        val logline: String? = null,
        /** Theme keyword(s) for tone alignment. */
        val theme: String? = null,
        /** Tone guidance for style prompts. */
        val tone: String? = null,
        /** Stakes summary for narrative pressure. */
        val stakes: String? = null,
        /** Default POV style for the story. */
        val pov: String? = null,
        /** Default tense style for the story. */
        val tense: String? = null,
        /** Target audience note for AI guidance. */
        val targetAudience: String? = null,
        /** Pacing guidance for AI prompts. */
        val pacing: String? = null,
    )
}
