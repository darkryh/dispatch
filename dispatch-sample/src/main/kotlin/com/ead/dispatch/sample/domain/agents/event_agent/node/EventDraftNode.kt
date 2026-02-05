package com.ead.dispatch.sample.domain.agents.event_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.event_agent.EventAIDraft
import com.ead.dispatch.sample.domain.agents.event_agent.EventAIRequest
import com.ead.dispatch.sample.domain.agents.event_agent.eventAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateEventDraft(
    name: String? = null,
): AIAgentNodeDelegate<EventAIRequest, Result<StructuredResponse<EventAIDraft>>> =
    node(name) { input -> generateEventDraft(input) }

private suspend fun AIAgentContext.generateEventDraft(
    request: EventAIRequest,
): Result<StructuredResponse<EventAIDraft>> = llm.writeSession {
    this.model = AIProvider.deepseekChatLlmModel

    rewritePrompt {
        eventAgentPrompt(request)
    }

    requestLLMStructured<EventAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.deepseekChatLlmModel,
            retries = 2,
        )
    )
}
