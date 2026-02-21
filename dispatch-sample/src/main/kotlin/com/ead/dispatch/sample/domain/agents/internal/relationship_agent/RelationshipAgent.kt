package com.ead.dispatch.sample.domain.agents.internal.relationship_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.node.nodeGenerateRelationshipDraft

class RelationshipAgent {
    suspend fun generateDraft(request: RelationshipAIRequest): RelationshipAIDraft {
        val agentName = AIProvider.getRelationshipAgentId(request.storyId)
        val temperature = when (request.mode) {
            RelationshipAIMode.NORMAL -> 0.7
            RelationshipAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<RelationshipAIRequest, Result<StructuredResponse<RelationshipAIDraft>>>(
            promptExecutor = AIProvider.Sync.subAgentExecutor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<RelationshipAIRequest, Result<StructuredResponse<RelationshipAIDraft>>>("relationship-draft") {
                val draftNode by nodeGenerateRelationshipDraft()
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
