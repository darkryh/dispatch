package com.ead.dispatch.sample.domain.agents.culture_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.culture_agent.node.nodeGenerateCultureDraft

class CultureAgent {
    suspend fun generateDraft(request: CultureAIRequest): CultureAIDraft {
        val agentName = AIProvider.getCultureAgentId(request.storyId)
        val temperature = when (request.mode) {
            CultureAIMode.NORMAL -> 0.7
            CultureAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<CultureAIRequest, Result<StructuredResponse<CultureAIDraft>>>(
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<CultureAIRequest, Result<StructuredResponse<CultureAIDraft>>>("culture-draft") {
                val draftNode by nodeGenerateCultureDraft()
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
