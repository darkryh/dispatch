package com.ead.dispatch.sample.data.db.entities

import kotlinx.serialization.Serializable

@Serializable
data class StoryEventRecord(
    val id: String,
    val storyId: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
)
