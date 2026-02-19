package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.streaming.StreamFrame
import com.ead.koog.context.orchestrator.api.ContextualResponse
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatIntentSignal
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ClassifiedChatTurn
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.buildTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.classifyTurnIntentWithAI
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeChatTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.updateChatTurnMetrics
import kotlinx.coroutines.flow.Flow

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeClassifyIntent(
    name: String? = null,
): AIAgentNodeDelegate<ChatRequest, ClassifiedChatTurn> =
    node(name ?: "classify-intent") { request ->
        val intentSignal = classifyTurnIntentWithAI(request)
        ClassifiedChatTurn(
            request = request,
            intentClass = intentSignal.intentClass,
            explicitWriteIntent = intentSignal.explicitWriteIntent,
            confidence = intentSignal.confidence,
            evidenceSpan = intentSignal.evidenceSpan,
            reasoning = intentSignal.reasoning,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidence = intentSignal.preferenceConfidence,
            preferenceEvidenceSpan = intentSignal.preferenceEvidenceSpan,
            preferenceReasoning = intentSignal.preferenceReasoning,
            resolvedAction = intentSignal.resolvedAction,
            confidenceBand = intentSignal.confidenceBand,
            riskClass = intentSignal.riskClass,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = intentSignal.requiresConfirmation,
            executionIntent = intentSignal.executionIntent,
        )
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeApplyTurnPolicy(
    name: String? = null,
): AIAgentNodeDelegate<ClassifiedChatTurn, ChatTurnInput> =
    node(name ?: "apply-turn-policy") { classified ->
        val policy = buildTurnPolicy(
            request = classified.request,
            intentSignal = ChatIntentSignal(
                intentClass = classified.intentClass,
                explicitWriteIntent = classified.explicitWriteIntent,
                confidence = classified.confidence,
                evidenceSpan = classified.evidenceSpan,
                reasoning = classified.reasoning,
                shouldSavePreference = classified.shouldSavePreference,
                preferenceConceptKeywords = classified.preferenceConceptKeywords,
                preferenceConfidence = classified.preferenceConfidence,
                preferenceEvidenceSpan = classified.preferenceEvidenceSpan,
                preferenceReasoning = classified.preferenceReasoning,
                resolvedAction = classified.resolvedAction,
                confidenceBand = classified.confidenceBand,
                riskClass = classified.riskClass,
                anchorHint = classified.anchorHint,
                requiresConfirmation = classified.requiresConfirmation,
                executionIntent = classified.executionIntent,
            ),
        )
        storeChatTurnPolicy(policy)
        ChatTurnInput(
            request = classified.request,
            policy = policy,
        )
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeAuditTurn(
    name: String? = null,
): AIAgentNodeDelegate<ContextualResponse<Flow<StreamFrame>>, ContextualResponse<Flow<StreamFrame>>> =
    node(name ?: "audit-turn") { response ->
        updateChatTurnMetrics { metrics ->
            metrics.auditNodeVisited = true
        }
        response
    }
