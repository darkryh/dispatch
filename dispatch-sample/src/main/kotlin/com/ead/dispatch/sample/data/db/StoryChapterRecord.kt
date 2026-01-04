package com.ead.dispatch.sample.data.db

data class StoryChapterRecord(
    val id: String,
    val volumeId: String,
    val number: Long,
    val title: String,
    val summary: String? = null,
    val content: String?,
    val contentRef: String?,
    val wordCount: Long,
    val pov: String? = null,
    val emotionalBeat: String? = null,
    val keyEvents: List<String> = emptyList(),
    val targetWordCount: Long? = null,
    val location: String? = null,
    val timeSpan: String? = null,
    val status: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
