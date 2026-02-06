package com.ead.dispatch.sample.domain.agents.chat_agent.util

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.persistence
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.telemetry.ContextCheckpointProperties
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf

@OptIn(ExperimentalStdlibApi::class, InternalAgentsApi::class)
suspend fun saveCheckpointForHistory(
    context: AIAgentGraphContextBase,
    request: ChatRequest,
    nodePath: String,
    contextSnapshot: ContextSnapshot? = null,
) {
    val inputJson = SerializationUtils.encodeDataToJsonElementOrNull(request, typeOf<ChatRequest>())
        ?: return

    val messageHistory = context.llm.readSession { prompt.messages }
    val parent = context.persistence().getLatestCheckpoint(context.agentId)
    val properties = ContextCheckpointProperties.merge(parent?.properties, contextSnapshot)

    val checkpoint = AgentCheckpointData(
        checkpointId = context.runId,
        createdAt = Clock.System.now(),
        nodePath = nodePath,
        lastInput = inputJson,
        messageHistory = messageHistory,
        version = parent?.version?.plus(1) ?: 0L,
        properties = properties,
    )

    context.persistence().saveCheckpoint(context.agentId, checkpoint)
}
