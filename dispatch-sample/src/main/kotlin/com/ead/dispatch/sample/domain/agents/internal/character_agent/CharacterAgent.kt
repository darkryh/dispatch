package com.ead.dispatch.sample.domain.agents.internal.character_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.character_agent.node.nodeGenerateCharacterDraft

class CharacterAgent {
    suspend fun generateDraft(request: CharacterAIRequest): CharacterAIDraft {
        val agentName = AIProvider.getCharacterAgentId(request.storyId)
        val temperature = when (request.mode) {
            CharacterAIMode.NORMAL -> 0.7
            CharacterAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<CharacterAIRequest, Result<StructuredResponse<CharacterAIDraft>>>(
            promptExecutor = AIProvider.Sync.subAgentExecutor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<CharacterAIRequest, Result<StructuredResponse<CharacterAIDraft>>>("character-draft") {
                val draftNode by nodeGenerateCharacterDraft()
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
