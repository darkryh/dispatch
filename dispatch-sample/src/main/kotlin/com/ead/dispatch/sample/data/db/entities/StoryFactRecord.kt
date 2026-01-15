package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.StoryFactType

data class StoryFactRecord(
    /** Stable identifier for the fact record. */
    val id: String,
    /** Parent story id that owns this fact. */
    val storyId: String,
    /** Fact type used for grouping and prompts. */
    val factType: StoryFactType,
    /** Fact text used for retrieval. */
    val content: String,
    /** Creation time for audits. */
    val createdAt: Long,
)
