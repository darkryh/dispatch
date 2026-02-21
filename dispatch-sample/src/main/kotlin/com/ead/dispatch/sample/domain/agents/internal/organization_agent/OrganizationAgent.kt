package com.ead.dispatch.sample.domain.agents.internal.organization_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.organization_agent.node.nodeGenerateOrganizationDraft

class OrganizationAgent {
    suspend fun generateDraft(request: OrganizationAIRequest): OrganizationAIDraft {
        val agentName = AIProvider.getOrganizationAgentId(request.storyId)
        val temperature = when (request.mode) {
            OrganizationAIMode.NORMAL -> 0.7
            OrganizationAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<OrganizationAIRequest, Result<StructuredResponse<OrganizationAIDraft>>>(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<OrganizationAIRequest, Result<StructuredResponse<OrganizationAIDraft>>>("organization-draft") {
                val draftNode by nodeGenerateOrganizationDraft()
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
