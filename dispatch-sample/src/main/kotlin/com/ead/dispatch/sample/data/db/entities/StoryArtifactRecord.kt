package com.ead.dispatch.sample.data.db.entities

import kotlinx.serialization.Serializable

@Serializable
data class StoryArtifactRecord(
    val id: String,
    val storyId: String,
    val name: String,
    val description: String? = null,
    val ownerId: String? = null,
    val ownerType: String? = null,
    val locationId: String? = null,
    val createdAt: Long,
)
