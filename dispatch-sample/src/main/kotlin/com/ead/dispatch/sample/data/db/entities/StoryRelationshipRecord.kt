package com.ead.dispatch.sample.data.db.entities

import kotlinx.serialization.Serializable

@Serializable
data class StoryRelationshipRecord(
    val id: String,
    val storyId: String,
    val subjectId: String,
    val subjectType: String,
    val objectId: String,
    val objectType: String,
    val relation: String,
    val notes: String? = null,
    val createdAt: Long,
)
