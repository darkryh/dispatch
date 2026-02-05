package com.ead.dispatch.sample.domain.agents.world_rule_agent.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleAIDraft
import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleAIRequest
import com.ead.dispatch.sample.domain.agents.world_rule_agent.worldRuleAgentPrompt

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeGenerateWorldRuleDraft(
    name: String? = null,
): AIAgentNodeDelegate<WorldRuleAIRequest, Result<StructuredResponse<WorldRuleAIDraft>>> =
    node(name) { input -> generateWorldRuleDraft(input) }

private suspend fun AIAgentContext.generateWorldRuleDraft(
    request: WorldRuleAIRequest,
): Result<StructuredResponse<WorldRuleAIDraft>> = llm.writeSession {
    this.model = AIProvider.deepseekChatLlmModel

    rewritePrompt {
        worldRuleAgentPrompt(request)
    }

    requestLLMStructured<WorldRuleAIDraft>(
        fixingParser = StructureFixingParser(
            model = AIProvider.deepseekChatLlmModel,
            retries = 2,
        )
    )
}
