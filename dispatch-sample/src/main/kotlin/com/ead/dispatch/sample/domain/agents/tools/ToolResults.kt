package com.ead.dispatch.sample.domain.agents.tools

import com.ead.dispatch.sample.domain.agents.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.ToolError
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult

fun <T> querySuccess(
    entity: OperationEntity,
    storyId: String,
    entityId: String?,
    summary: String,
    payload: T,
    warnings: List<String> = emptyList(),
): ToolResult<QueryOutcome<T>> = ToolResult.Success(
    data = QueryOutcome(
        entity = entity,
        storyId = storyId,
        entityId = entityId,
        summary = summary,
        payload = payload,
    ),
    message = summary,
    warnings = warnings,
)

fun success(
    action: String,
    entity: OperationEntity,
    storyId: String,
    entityId: String?,
    summary: String,
    warnings: List<String> = emptyList(),
): ToolResult<OperationOutcome> = ToolResult.Success(
    data = OperationOutcome(
        action = action,
        entity = entity,
        storyId = storyId,
        entityId = entityId,
        summary = summary,
    ),
    message = summary,
    warnings = warnings,
)

fun <T> failure(
    code: String,
    message: String,
    details: String? = null,
): ToolResult<T> = ToolResult.Failure(
    error = ToolError(code = code, details = details),
    message = message,
)
