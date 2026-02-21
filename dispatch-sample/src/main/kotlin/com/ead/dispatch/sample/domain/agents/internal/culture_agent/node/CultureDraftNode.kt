package com.ead.dispatch.sample.domain.agents.internal.culture_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.culture_agent.CultureAIDraft
import com.ead.dispatch.sample.domain.agents.internal.culture_agent.CultureAIRequest
import com.ead.dispatch.sample.domain.agents.internal.culture_agent.cultureAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateCultureDraft(
    name: String? = null,
): AIAgentNodeDelegate<CultureAIRequest, Result<StructuredResponse<CultureAIDraft>>> =
    node(name) { input -> generateCultureDraft(input) }

private suspend fun AIAgentContext.generateCultureDraft(
    request: CultureAIRequest,
): Result<StructuredResponse<CultureAIDraft>> = llm.writeSession {
    rewritePrompt {
        cultureAgentPrompt(request)
    }

    requestLLMStructured<CultureAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2
        )
    )
}
