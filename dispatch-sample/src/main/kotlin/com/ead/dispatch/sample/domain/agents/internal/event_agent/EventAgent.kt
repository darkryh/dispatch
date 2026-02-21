package com.ead.dispatch.sample.domain.agents.internal.event_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.event_agent.node.nodeGenerateEventDraft

class EventAgent {
    suspend fun generateDraft(request: EventAIRequest): EventAIDraft {
        val agentName = AIProvider.getEventAgentId(request.storyId)
        val temperature = when (request.mode) {
            EventAIMode.NORMAL -> 0.7
            EventAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<EventAIRequest, Result<StructuredResponse<EventAIDraft>>>(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<EventAIRequest, Result<StructuredResponse<EventAIDraft>>>("event-draft") {
                val draftNode by nodeGenerateEventDraft()
                edge(nodeStart forwardTo draftNode)
                edge(draftNode forwardTo nodeFinish transformed { it })
            },
            maxIterations = 10,
            temperature = temperature,
            id = agentName,
        )

        val response = agent.run(request)
        val structured = response.getOrThrow()
        return structured.data
    }
}
