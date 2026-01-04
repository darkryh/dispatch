package com.ead.dispatch.sample.data.db

data class SessionRecord(
    val id: String,
    val title: String,
    val mode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Long,
    val metadataJson: String?,
)
