package com.ead.dispatch.sample.domain.agents.internal.character_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAIDraft
import com.ead.dispatch.sample.domain.agents.internal.character_agent.CharacterAIRequest
import com.ead.dispatch.sample.domain.agents.internal.character_agent.characterAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateCharacterDraft(
    name: String? = null,
): AIAgentNodeDelegate<CharacterAIRequest, Result<StructuredResponse<CharacterAIDraft>>> =
    node(name) { input -> generateCharacterDraft(input) }

private suspend fun AIAgentContext.generateCharacterDraft(
    request: CharacterAIRequest,
): Result<StructuredResponse<CharacterAIDraft>> = llm.writeSession {
    rewritePrompt {
        characterAgentPrompt(request)
    }

    requestLLMStructured<CharacterAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2,
        )
    )
}
