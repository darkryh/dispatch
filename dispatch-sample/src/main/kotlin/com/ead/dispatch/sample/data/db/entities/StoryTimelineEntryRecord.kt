package com.ead.dispatch.sample.data.db.entities

import kotlinx.serialization.Serializable

@Serializable
data class StoryTimelineEntryRecord(
    val id: String,
    val storyId: String,
    val title: String,
    val description: String? = null,
    val orderIndex: Long,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)
