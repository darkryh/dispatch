package com.ead.dispatch.sample.domain.agents.internal.location_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.location_agent.LocationAIDraft
import com.ead.dispatch.sample.domain.agents.internal.location_agent.LocationAIRequest
import com.ead.dispatch.sample.domain.agents.internal.location_agent.locationAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateLocationDraft(
    name: String? = null,
): AIAgentNodeDelegate<LocationAIRequest, Result<StructuredResponse<LocationAIDraft>>> =
    node(name) { input -> generateLocationDraft(input) }

private suspend fun AIAgentContext.generateLocationDraft(
    request: LocationAIRequest,
): Result<StructuredResponse<LocationAIDraft>> = llm.writeSession {
    rewritePrompt {
        locationAgentPrompt(request)
    }

    requestLLMStructured<LocationAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2
        )
    )
}
