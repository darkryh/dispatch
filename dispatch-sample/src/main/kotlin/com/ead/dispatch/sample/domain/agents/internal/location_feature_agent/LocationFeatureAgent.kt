package com.ead.dispatch.sample.domain.agents.internal.location_feature_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.node.nodeGenerateLocationFeatureDraft

class LocationFeatureAgent {
    suspend fun generateDraft(request: LocationFeatureAIRequest): LocationFeatureAIDraft {
        val agentName = AIProvider.getLocationFeatureAgentId(request.storyId)
        val temperature = when (request.mode) {
            LocationFeatureAIMode.NORMAL -> 0.7
            LocationFeatureAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<LocationFeatureAIRequest, Result<StructuredResponse<LocationFeatureAIDraft>>>(
            promptExecutor = AIProvider.Sync.subAgentExecutor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<LocationFeatureAIRequest, Result<StructuredResponse<LocationFeatureAIDraft>>>("location-feature-draft") {
                val draftNode by nodeGenerateLocationFeatureDraft()
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
