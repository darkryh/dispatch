package com.ead.dispatch.sample.domain.agents.internal.location_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.location_agent.node.nodeGenerateLocationDraft

class LocationAgent {
    suspend fun generateDraft(request: LocationAIRequest): LocationAIDraft {
        val agentName = AIProvider.getLocationAgentId(request.storyId)
        val temperature = when (request.mode) {
            LocationAIMode.NORMAL -> 0.7
            LocationAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<LocationAIRequest, Result<StructuredResponse<LocationAIDraft>>>(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<LocationAIRequest, Result<StructuredResponse<LocationAIDraft>>>("location-draft") {
                val draftNode by nodeGenerateLocationDraft()
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
