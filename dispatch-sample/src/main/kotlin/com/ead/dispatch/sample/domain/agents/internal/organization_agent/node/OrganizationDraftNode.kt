package com.ead.dispatch.sample.domain.agents.internal.organization_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.organization_agent.OrganizationAIDraft
import com.ead.dispatch.sample.domain.agents.internal.organization_agent.OrganizationAIRequest
import com.ead.dispatch.sample.domain.agents.internal.organization_agent.organizationAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateOrganizationDraft(
    name: String? = null,
): AIAgentNodeDelegate<OrganizationAIRequest, Result<StructuredResponse<OrganizationAIDraft>>> =
    node(name) { input -> generateOrganizationDraft(input) }

private suspend fun AIAgentContext.generateOrganizationDraft(
    request: OrganizationAIRequest,
): Result<StructuredResponse<OrganizationAIDraft>> = llm.writeSession {
    rewritePrompt {
        organizationAgentPrompt(request)
    }

    requestLLMStructured<OrganizationAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2
        )
    )
}
