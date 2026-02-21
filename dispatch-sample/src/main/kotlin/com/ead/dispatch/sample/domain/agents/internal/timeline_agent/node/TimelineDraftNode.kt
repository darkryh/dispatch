package com.ead.dispatch.sample.domain.agents.internal.timeline_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.TimelineAIDraft
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.TimelineAIRequest
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.timelineAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateTimelineDraft(
    name: String? = null,
): AIAgentNodeDelegate<TimelineAIRequest, Result<StructuredResponse<TimelineAIDraft>>> =
    node(name) { input -> generateTimelineDraft(input) }

private suspend fun AIAgentContext.generateTimelineDraft(
    request: TimelineAIRequest,
): Result<StructuredResponse<TimelineAIDraft>> = llm.writeSession {
    rewritePrompt {
        timelineAgentPrompt(request)
    }

    requestLLMStructured<TimelineAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2
        )
    )
}
