package com.ead.dispatch.sample.domain.agents.artifact_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.artifact_agent.node.nodeGenerateArtifactDraft

class ArtifactAgent {
    suspend fun generateDraft(request: ArtifactAIRequest): ArtifactAIDraft {
        val agentName = AIProvider.getArtifactAgentId(request.storyId)
        val temperature = when (request.mode) {
            ArtifactAIMode.NORMAL -> 0.7
            ArtifactAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<ArtifactAIRequest, Result<StructuredResponse<ArtifactAIDraft>>>(
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = ToolRegistry {},
            strategy = strategy<ArtifactAIRequest, Result<StructuredResponse<ArtifactAIDraft>>>("artifact-draft") {
                val draftNode by nodeGenerateArtifactDraft()
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
