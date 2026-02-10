package com.ead.dispatch.sample.data.db.entities

import kotlinx.serialization.Serializable

@Serializable
data class StoryWorldRuleRecord(
    val id: String,
    val storyId: String,
    val title: String,
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)
