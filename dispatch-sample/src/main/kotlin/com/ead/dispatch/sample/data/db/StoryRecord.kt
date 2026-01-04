package com.ead.dispatch.sample.data.db

data class StoryRecord(
    val id: String,
    val sessionId: String,
    val title: String?,
    val genre: String?,
    val setting: String?,
    val plotOutline: String?,
    val logline: String? = null,
    val theme: String? = null,
    val tone: String? = null,
    val stakes: String? = null,
    val pov: String? = null,
    val tense: String? = null,
    val targetAudience: String? = null,
    val pacing: String? = null,
    val status: String? = null,
    val styleRefs: List<String> = emptyList(),
    val emotionalBeats: List<String> = emptyList(),
    val keyLocations: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)
