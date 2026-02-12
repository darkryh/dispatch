package com.ead.dispatch.sample.domain.agents.story_agent.util

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.SerializationUtils
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.persistence
import ai.koog.prompt.message.Message
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryTurnCheckpointProperties
import com.ead.dispatch.sample.domain.agents.story_agent.policy.currentStoryTurnMetrics
import com.ead.dispatch.sample.domain.agents.story_agent.policy.currentStoryTurnPolicy
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.telemetry.ContextCheckpointProperties
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf

@OptIn(ExperimentalStdlibApi::class, InternalAgentsApi::class)
suspend fun saveStoryCheckpointForHistory(
    context: AIAgentGraphContextBase,
    request: StoryRequest,
    nodePath: String,
    contextSnapshot: ContextSnapshot? = null,
) {
    val inputJson = SerializationUtils.encodeDataToJsonElementOrNull(request, typeOf<StoryRequest>())
        ?: return

    val messageHistory = context.llm.readSession { prompt.messages }
        .filterNot { message -> message is Message.System }
    val parent = context.persistence().getLatestCheckpoint(context.agentId)
    val baseProperties = ContextCheckpointProperties.merge(parent?.properties, contextSnapshot)
    val properties = StoryTurnCheckpointProperties.merge(
        existing = baseProperties,
        policy = context.currentStoryTurnPolicy(),
        metrics = context.currentStoryTurnMetrics(),
    )

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
