package com.ead.dispatch.sample.domain.agents.world_rule_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.world_rule_agent.node.nodeGenerateWorldRuleDraft

class WorldRuleAgent {
    suspend fun generateDraft(request: WorldRuleAIRequest): WorldRuleAIDraft {
        val agentName = AIProvider.getWorldRuleAgentId(request.storyId)
        val temperature = when (request.mode) {
            WorldRuleAIMode.NORMAL -> 0.7
            WorldRuleAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<WorldRuleAIRequest, Result<StructuredResponse<WorldRuleAIDraft>>>(
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<WorldRuleAIRequest, Result<StructuredResponse<WorldRuleAIDraft>>>("world-rule-draft") {
                val draftNode by nodeGenerateWorldRuleDraft()
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
