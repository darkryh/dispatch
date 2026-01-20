package com.ead.dispatch.sample.domain.agents.chat_agent.tools.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OperationEntity {
    STORY,
    CHARACTER,
    LOCATION,
    ARC,
    FACT,
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
