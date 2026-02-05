package com.ead.dispatch.sample.domain.agents.artifact_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIDraft
import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIRequest
import com.ead.dispatch.sample.domain.agents.artifact_agent.artifactAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateArtifactDraft(
    name: String? = null,
): AIAgentNodeDelegate<ArtifactAIRequest, Result<StructuredResponse<ArtifactAIDraft>>> =
    node(name) { input -> generateArtifactDraft(input) }

private suspend fun AIAgentContext.generateArtifactDraft(
    request: ArtifactAIRequest,
): Result<StructuredResponse<ArtifactAIDraft>> = llm.writeSession {
    this.model = AIProvider.deepseekChatLlmModel

    rewritePrompt {
        artifactAgentPrompt(request)
    }

    requestLLMStructured<ArtifactAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.deepseekChatLlmModel,
            retries = 2,
        )
    )
}
