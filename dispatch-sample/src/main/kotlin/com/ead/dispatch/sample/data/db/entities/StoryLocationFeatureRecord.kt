package com.ead.dispatch.sample.data.db.entities

import kotlinx.serialization.Serializable

@Serializable
data class StoryLocationFeatureRecord(
    val id: String,
    val storyId: String,
    val locationId: String? = null,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
)
