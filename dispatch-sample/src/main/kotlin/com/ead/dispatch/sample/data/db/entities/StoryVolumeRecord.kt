package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.ContentStatus
import kotlinx.serialization.Serializable

@Serializable
data class StoryVolumeRecord(
    /** Stable identifier for the volume record. */
    val id: String,
    /** Parent story id for ordering and grouping. */
    val storyId: String,
    /** Volume number within the story. */
    val number: Long,
    /** Display title used in the UI. */
    val title: String,
    /** Planning metadata grouped for clarity. */
    val plan: VolumePlan? = null,
    /** High-level events for retrieval (normalized list). */
    val keyEvents: List<String> = emptyList(),
    /** Creation time for ordering and audits. */
    val createdAt: Long,
    /** Last update time for metadata changes. */
    val updatedAt: Long,
) {
    @Serializable
    data class VolumePlan(
        /** Short volume summary for planning. */
        val summary: String? = null,
        /** Optional target word count for pacing. */
        val targetWordCount: Long? = null,
        /** Draft status for workflow control. */
        val status: ContentStatus? = null,
        /** Private notes for planning. */
        val notes: String? = null,
    )
}
