package com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.LocationFeatureAIDraft
import com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.LocationFeatureAIRequest
import com.ead.dispatch.sample.domain.agents.internal.location_feature_agent.locationFeatureAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateLocationFeatureDraft(
    name: String? = null,
): AIAgentNodeDelegate<LocationFeatureAIRequest, Result<StructuredResponse<LocationFeatureAIDraft>>> =
    node(name) { input -> generateLocationFeatureDraft(input) }

private suspend fun AIAgentContext.generateLocationFeatureDraft(
    request: LocationFeatureAIRequest,
): Result<StructuredResponse<LocationFeatureAIDraft>> = llm.writeSession {
    rewritePrompt {
        locationFeatureAgentPrompt(request)
    }

    requestLLMStructured<LocationFeatureAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.SubAgent.fixer,
            retries = 2
        )
    )
}
