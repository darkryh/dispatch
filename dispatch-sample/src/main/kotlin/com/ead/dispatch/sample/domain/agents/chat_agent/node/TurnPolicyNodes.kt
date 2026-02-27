package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.streaming.StreamFrame
import com.ead.koog.context.orchestrator.api.ContextRunOutput
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatIntentSignal
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ClassifiedChatTurn
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.buildTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.classifyTurnIntentWithAI
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeChatTurnRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.storeChatTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.updateChatTurnMetrics
import com.ead.dispatch.sample.domain.agents.chat_agent.util.saveCheckpointForHistory
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
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = intentSignal.resolvedAction,
            confidenceBand = intentSignal.confidenceBand,
            riskClass = intentSignal.riskClass,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = intentSignal.requiresConfirmation,
            requiresCreativeChoice = intentSignal.requiresCreativeChoice,
            decisionBeforePersist = intentSignal.decisionBeforePersist,
            executionIntent = intentSignal.executionIntent,
        )
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeApplyTurnPolicy(
    name: String? = null,
): AIAgentNodeDelegate<ClassifiedChatTurn, ChatTurnInput> =
    node(name ?: "apply-turn-policy") { classified ->
        val nodePath = executionInfo.path()
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
                preferenceConfidenceBand = classified.preferenceConfidenceBand,
                preferenceNovelty = classified.preferenceNovelty,
                resolvedAction = classified.resolvedAction,
                confidenceBand = classified.confidenceBand,
                riskClass = classified.riskClass,
                anchorHint = classified.anchorHint,
                requiresConfirmation = classified.requiresConfirmation,
                requiresCreativeChoice = classified.requiresCreativeChoice,
                decisionBeforePersist = classified.decisionBeforePersist,
                executionIntent = classified.executionIntent,
            ),
        )
        storeChatTurnPolicy(policy)
        storeChatTurnRequest(classified.request)
        // Additive debug checkpoint at policy stage; final chat history checkpoint remains in streaming node.
        saveCheckpointForHistory(
            context = this,
            request = classified.request,
            nodePath = nodePath,
            contextSnapshot = null,
        )
        ChatTurnInput(
            request = classified.request,
            policy = policy,
        )
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeAuditTurn(
    name: String? = null,
): AIAgentNodeDelegate<ContextRunOutput<Flow<StreamFrame>>, ContextRunOutput<Flow<StreamFrame>>> =
    node(name ?: "audit-turn") { response ->
        updateChatTurnMetrics { metrics ->
            metrics.auditNodeVisited = true
        }
        response
    }
