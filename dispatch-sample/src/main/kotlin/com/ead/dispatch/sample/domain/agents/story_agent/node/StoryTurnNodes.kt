package com.ead.dispatch.sample.domain.agents.story_agent.node

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryIntentSignal
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryTurnPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.policy.buildStoryTurnPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.policy.classifyStoryTurnIntentWithAI
import com.ead.dispatch.sample.domain.agents.story_agent.policy.storeStoryTurnRequest
import com.ead.dispatch.sample.domain.agents.story_agent.policy.storeStoryTurnPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.policy.updateStoryTurnMetrics
import com.ead.koog.context.orchestrator.api.ContextRunOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class StoryTurnInput(
    val request: StoryRequest,
    val policy: StoryTurnPolicy,
)

@Serializable
data class ClassifiedStoryTurn(
    val request: StoryRequest,
    val intentSignal: StoryIntentSignal,
)

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeClassifyStoryIntent(
    name: String? = null,
): AIAgentNodeDelegate<StoryRequest, ClassifiedStoryTurn> =
    node(name ?: "story-classify-intent") { request ->
        val signal = classifyStoryTurnIntentWithAI(request)
        ClassifiedStoryTurn(
            request = request,
            intentSignal = signal,
        )
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeApplyStoryTurnPolicy(
    name: String? = null,
): AIAgentNodeDelegate<ClassifiedStoryTurn, StoryTurnInput> =
    node(name ?: "story-apply-turn-policy") { classified ->
        val policy = buildStoryTurnPolicy(
            request = classified.request,
            intentSignal = classified.intentSignal,
        )
        storeStoryTurnPolicy(policy)
        storeStoryTurnRequest(classified.request)
        StoryTurnInput(
            request = classified.request,
            policy = policy,
        )
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeAuditStoryTurn(
    name: String? = null,
): AIAgentNodeDelegate<ContextRunOutput<Flow<StreamFrame>>, ContextRunOutput<Flow<StreamFrame>>> =
    node(name ?: "story-audit-turn") { response ->
        updateStoryTurnMetrics { metrics ->
            metrics.auditNodeVisited = true
        }
        response
    }
