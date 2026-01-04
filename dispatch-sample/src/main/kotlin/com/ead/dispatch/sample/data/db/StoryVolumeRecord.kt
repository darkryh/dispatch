package com.ead.dispatch.sample.data.db

data class StoryVolumeRecord(
    val id: String,
    val storyId: String,
    val number: Long,
    val title: String,
    val summary: String?,
    val arc: String? = null,
    val targetWordCount: Long? = null,
    val keyEvents: List<String> = emptyList(),
    val status: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
