package com.ead.dispatch.sample.data.db

data class RagDocumentRecord(
    val docId: String,
    val sessionId: String,
    val storyId: String?,
    val volumeId: String?,
    val chapterId: String?,
    val chunkIndex: Long,
    val sourceType: String,
    val sourceRef: String?,
    val checksum: String?,
    val createdAt: Long,
)
