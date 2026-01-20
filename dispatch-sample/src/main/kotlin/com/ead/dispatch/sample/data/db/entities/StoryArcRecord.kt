package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import kotlinx.serialization.Serializable

@Serializable
data class StoryArcRecord(
    /** Stable identifier for the arc record. */
    val id: String,
    /** Parent story id that owns this arc. */
    val storyId: String,
    /** Scope where this arc applies (story/volume/chapter/scene). */
    val scopeType: ArcScope,
    /** Scope target id, null only when scope is STORY. */
    val scopeId: String? = null,
    /** Arc title for outline displays. */
    val title: String,
    /** Short arc summary for prompts. */
    val summary: String? = null,
    /** Draft status for workflow control. */
    val status: ContentStatus? = null,
    /** Creation time for audits and ordering. */
    val createdAt: Long,
    /** Last update time for metadata changes. */
    val updatedAt: Long,
)
