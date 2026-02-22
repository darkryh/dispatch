package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import kotlinx.serialization.Serializable

@Serializable
enum class ChatIntentClass {
    CREATIVE,
    WRITE,
    AMBIGUOUS,
    DESTRUCTIVE,
}

@Serializable
enum class ChatDecisionPath {
    DIRECT_RESPONSE,
    DIRECT_WRITE,
    SELECTOR,
    FOLLOW_UP,
}

@Serializable
enum class ChatPreferenceNovelty {
    NEW,
    ALREADY_KNOWN,
    UNCERTAIN,
}

@Serializable
data class ChatIntentSignal(
    val intentClass: ChatIntentClass = ChatIntentClass.AMBIGUOUS,
    val explicitWriteIntent: Boolean = false,
    val confidence: Double = 0.0,
    val evidenceSpan: String = "",
    val reasoning: String = "",
    val shouldSavePreference: Boolean = false,
    val preferenceConceptKeywords: List<String> = emptyList(),
    val preferenceConfidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val preferenceNovelty: ChatPreferenceNovelty = ChatPreferenceNovelty.UNCERTAIN,
    val resolvedAction: IntentResolvedAction = IntentResolvedAction.FOLLOW_UP,
    val confidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val riskClass: IntentRiskClass = IntentRiskClass.SAFE,
    val anchorHint: String = "",
    val requiresConfirmation: Boolean = false,
    val requiresCreativeChoice: Boolean = false,
    val decisionBeforePersist: Boolean = false,
    val executionIntent: IntentExecutionIntent = IntentExecutionIntent.EXECUTE,
)

@Serializable
data class ClassifiedChatTurn(
    val request: ChatRequest,
    val intentClass: ChatIntentClass,
    val explicitWriteIntent: Boolean = false,
    val confidence: Double = 0.0,
    val evidenceSpan: String = "",
    val reasoning: String = "",
    val shouldSavePreference: Boolean = false,
    val preferenceConceptKeywords: List<String> = emptyList(),
    val preferenceConfidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val preferenceNovelty: ChatPreferenceNovelty = ChatPreferenceNovelty.UNCERTAIN,
    val resolvedAction: IntentResolvedAction = IntentResolvedAction.FOLLOW_UP,
    val confidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val riskClass: IntentRiskClass = IntentRiskClass.SAFE,
    val anchorHint: String = "",
    val requiresConfirmation: Boolean = false,
    val requiresCreativeChoice: Boolean = false,
    val decisionBeforePersist: Boolean = false,
    val executionIntent: IntentExecutionIntent = IntentExecutionIntent.EXECUTE,
)

@Serializable
data class ChatTurnPolicy(
    val intentClass: ChatIntentClass,
    val decisionPath: ChatDecisionPath,
    val explicitWriteIntent: Boolean,
    val allowWriteTools: Boolean,
    val requireSelectorForDestructive: Boolean,
    val requireSelectorForCreative: Boolean = false,
    val rationale: String,
    val fromDecisionPrompt: Boolean,
    val requestTextHash: String = "",
    val shouldSavePreference: Boolean = false,
    val preferenceConceptKeywords: List<String> = emptyList(),
    val preferenceConfidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val preferenceNovelty: ChatPreferenceNovelty = ChatPreferenceNovelty.UNCERTAIN,
    val resolvedAction: IntentResolvedAction = IntentResolvedAction.FOLLOW_UP,
    val confidenceBand: IntentConfidenceBand = IntentConfidenceBand.LOW,
    val riskClass: IntentRiskClass = IntentRiskClass.SAFE,
    val anchorHint: String = "",
    val requiresConfirmation: Boolean = false,
    val requiresCreativeChoice: Boolean = false,
    val decisionBeforePersist: Boolean = false,
    val executionIntent: IntentExecutionIntent = IntentExecutionIntent.EXECUTE,
)

@Serializable
data class ChatTurnInput(
    val request: ChatRequest,
    val policy: ChatTurnPolicy,
)

@Serializable
data class ChatTurnMetrics(
    val intentClass: ChatIntentClass,
    val decisionPath: ChatDecisionPath,
    var preferenceSaveRecommended: Boolean = false,
    var preferenceConceptsSuggested: String = "",
    var preferenceConfidenceBand: String = "",
    var preferenceNovelty: String = "",
    var preferenceSaveExecuted: Boolean = false,
    var preferenceSaveSkippedReason: String = "",
    var requestedToolCalls: Int = 0,
    var executedToolCalls: Int = 0,
    var blockedToolCalls: Int = 0,
    var failedToolCalls: Int = 0,
    var writeToolCalls: Int = 0,
    var writeToolCallsBlocked: Int = 0,
    var decisionToolCalls: Int = 0,
    var mutationCreateCount: Int = 0,
    var mutationUpdateCount: Int = 0,
    var mutationDeleteCount: Int = 0,
    var selectorShown: Boolean = false,
    var auditNodeVisited: Boolean = false,
)

fun ChatTurnPolicy.toMetrics(): ChatTurnMetrics = ChatTurnMetrics(
    intentClass = intentClass,
    decisionPath = decisionPath,
    preferenceSaveRecommended = shouldSavePreference,
    preferenceConceptsSuggested = preferenceConceptKeywords.joinToString(","),
    preferenceConfidenceBand = preferenceConfidenceBand.name,
    preferenceNovelty = preferenceNovelty.name,
)
