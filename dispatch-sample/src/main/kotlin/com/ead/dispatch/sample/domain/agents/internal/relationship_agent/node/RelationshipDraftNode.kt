package com.ead.dispatch.sample.domain.agents.internal.relationship_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.RelationshipAIDraft
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.RelationshipAIRequest
import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.relationshipAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateRelationshipDraft(
    name: String? = null,
): AIAgentNodeDelegate<RelationshipAIRequest, Result<StructuredResponse<RelationshipAIDraft>>> =
    node(name) { input -> generateRelationshipDraft(input) }

private suspend fun AIAgentContext.generateRelationshipDraft(
    request: RelationshipAIRequest,
): Result<StructuredResponse<RelationshipAIDraft>> = llm.writeSession {
    rewritePrompt {
        relationshipAgentPrompt(request)
    }

    requestLLMStructured<RelationshipAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2
        )
    )
}
