package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.ContentStatus
import kotlinx.serialization.Serializable

@Serializable
data class StorySceneRecord(
    /** Stable identifier for the scene record. */
    val id: String,
    /** Parent chapter id containing this scene. */
    val chapterId: String,
    /** Scene number within the chapter. */
    val number: Long,
    /** Optional scene label for outlines. */
    val title: String? = null,
    /** Short scene summary for AI planning. */
    val summary: String? = null,
    /** Scene context grouped for retrieval and continuity. */
    val context: SceneContext? = null,
    /** Key beats to drive AI prompts and search (normalized list). */
    val keyEvents: List<String> = emptyList(),
    /** Draft status for workflow control. */
    val status: ContentStatus? = null,
    /** Creation time for ordering and audits. */
    val createdAt: Long,
    /** Last update time for metadata changes. */
    val updatedAt: Long,
) {
    @Serializable
    data class SceneContext(
        /** File range pointing to the scene section in chapter markdown. */
        val range: String? = null,
        /** POV tag for retrieval and consistency checks. */
        val pov: String? = null,
        /** Emotional beat for pacing and analysis. */
        val emotionalBeat: String? = null,
        /** Location reference for geography and continuity. */
        val locationId: String? = null,
        /** Time span or timestamp label for the scene. */
        val timeSpan: String? = null,
    )
}
