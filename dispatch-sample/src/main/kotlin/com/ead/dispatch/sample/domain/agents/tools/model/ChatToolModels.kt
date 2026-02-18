package com.ead.dispatch.sample.domain.agents.tools.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OperationEntity {
    STORY,
    VOLUME,
    CHAPTER,
    SCENE,
    CHARACTER,
    LOCATION,
    ARC,
    WORLD_RULE,
    CULTURE,
    EVENT,
    ORGANIZATION,
    RELATIONSHIP,
    LOCATION_FEATURE,
    ARTIFACT,
    TIMELINE_ENTRY,
}

@Serializable
data class OperationOutcome(
    val action: String,
    val entity: OperationEntity,
    val storyId: String,
    val entityId: String? = null,
    val summary: String,
)

@Serializable
data class ChapterDraftPayload(
    val chapterId: String,
    val text: String,
    val checksum: String,
    val wordCount: Long,
    val updatedAt: Long,
    val versionId: String,
)

@Serializable
data class ChapterDraftProposalPayload(
    val proposalId: String,
    val chapterId: String,
    val baseChecksum: String,
    val candidateChecksum: String,
    val baseText: String,
    val candidateText: String,
    val operationCount: Int,
    val preview: String,
    val createdAt: Long,
    val note: String? = null,
)

@Serializable
enum class DraftValidationSeverity {
    INFO,
    WARNING,
    ERROR,
}

@Serializable
data class DraftValidationIssue(
    val code: String,
    val severity: DraftValidationSeverity,
    val message: String,
)

@Serializable
data class ChapterDraftValidationPayload(
    val chapterId: String,
    val checksum: String,
    val wordCount: Long,
    val targetWordCount: Long? = null,
    val issues: List<DraftValidationIssue> = emptyList(),
)

@Serializable
data class ToolError(
    val code: String,
    val details: String? = null,
)

@Serializable
sealed interface ToolResult<out T> {
    @Serializable
    @SerialName("success")
    data class Success<T>(
        val data: T,
        val message: String,
        val warnings: List<String> = emptyList(),
    ) : ToolResult<T>

    @Serializable
    @SerialName("failure")
    data class Failure(
        val error: ToolError,
        val message: String,
    ) : ToolResult<Nothing>
}

@Serializable
data class QueryOutcome<T>(
    val entity: OperationEntity,
    val storyId: String,
    val entityId: String? = null,
    val summary: String,
    val payload: T,
)
