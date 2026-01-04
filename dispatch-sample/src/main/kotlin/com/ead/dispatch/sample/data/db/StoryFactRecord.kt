package com.ead.dispatch.sample.data.db

data class StoryFactRecord(
    val id: String,
    val storyId: String,
    val factType: StoryFactType,
    val content: String,
    val createdAt: Long,
)
