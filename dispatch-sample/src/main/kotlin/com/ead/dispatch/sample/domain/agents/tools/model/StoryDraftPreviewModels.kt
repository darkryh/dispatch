package com.ead.dispatch.sample.domain.agents.tools.model

import kotlinx.serialization.Serializable

@Serializable
enum class StoryDraftPreviewStatus {
    PENDING,
    APPLIED,
    REJECTED,
}

@Serializable
data class StoryDraftPreviewPendingRange(
    val startLine: Int,
    val endLine: Int,
    val changedAtEpochMillis: Long,
)

@Serializable
data class StoryDraftPreviewSnapshot(
    val storyId: String,
    val chapterId: String,
    val volumeNumber: Long? = null,
    val volumeTitle: String? = null,
    val chapterNumber: Long? = null,
    val chapterTitle: String,
    val beforeText: String,
    val afterText: String,
    val status: StoryDraftPreviewStatus,
    val proposalId: String? = null,
    val pendingRanges: List<StoryDraftPreviewPendingRange> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
