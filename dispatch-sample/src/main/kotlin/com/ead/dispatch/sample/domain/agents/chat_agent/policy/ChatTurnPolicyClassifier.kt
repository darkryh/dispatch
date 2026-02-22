package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest

private const val explicitWriteConfidenceThreshold = 0.55
private const val writeClassConfidenceThreshold = 0.40

fun buildTurnPolicy(
    request: ChatRequest,
    intentSignal: ChatIntentSignal,
): ChatTurnPolicy {
    val requestTextHash = request.text.normalizedStableHash()

    if (request.fromDecisionPrompt) {
        return ChatTurnPolicy(
            intentClass = ChatIntentClass.WRITE,
            decisionPath = ChatDecisionPath.DIRECT_WRITE,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "User answered a selector prompt; continue execution with writes enabled.",
            fromDecisionPrompt = true,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = IntentResolvedAction.WRITE_UPDATE,
            confidenceBand = IntentConfidenceBand.HIGH,
            riskClass = IntentRiskClass.SAFE,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )
    }

    val intentClass = intentSignal.intentClass
    val confidence = intentSignal.confidence.coerceIn(0.0, 1.0)
    val explicitWriteIntent = intentSignal.explicitWriteIntent
    val resolvedAction = intentSignal.resolvedAction
    val confidenceBand = intentSignal.confidenceBand
    val riskClass = intentSignal.riskClass
    val requiresConfirmation = intentSignal.requiresConfirmation
    val requiresCreativeChoice = intentSignal.requiresCreativeChoice
    val decisionBeforePersist = intentSignal.decisionBeforePersist
    val executionIntent = intentSignal.executionIntent
    val writeAllowedBySignal = (explicitWriteIntent && confidence >= explicitWriteConfidenceThreshold) ||
        (intentClass == ChatIntentClass.WRITE && confidence >= writeClassConfidenceThreshold)
    val writeAllowedByResolution =
        resolvedAction == IntentResolvedAction.WRITE_CREATE || resolvedAction == IntentResolvedAction.WRITE_UPDATE
    val writeLikeByResolution =
        resolvedAction == IntentResolvedAction.WRITE_CREATE ||
            resolvedAction == IntentResolvedAction.WRITE_UPDATE ||
            resolvedAction == IntentResolvedAction.WRITE_DELETE
    val resolutionConfident = confidenceBand != IntentConfidenceBand.LOW
    val executeWriteReady =
        executionIntent == IntentExecutionIntent.EXECUTE &&
            explicitWriteIntent &&
            confidence >= explicitWriteConfidenceThreshold
    val destructiveByResolution =
        resolvedAction == IntentResolvedAction.WRITE_DELETE ||
            riskClass == IntentRiskClass.DESTRUCTIVE ||
            requiresConfirmation
    val creativeSelectorNeeded =
        executionIntent == IntentExecutionIntent.EXECUTE &&
            !destructiveByResolution &&
            (requiresCreativeChoice || decisionBeforePersist) &&
            resolvedAction == IntentResolvedAction.WRITE_CREATE &&
            (intentClass == ChatIntentClass.CREATIVE || intentClass == ChatIntentClass.WRITE)
    val inquiryWriteLike = executionIntent == IntentExecutionIntent.INQUIRE &&
        (intentClass == ChatIntentClass.WRITE ||
            intentClass == ChatIntentClass.DESTRUCTIVE ||
            explicitWriteIntent ||
            writeLikeByResolution)

    val evidence = intentSignal.evidenceSpan
        .trim()
        .take(120)
        .ifBlank { "none" }

    return when {
        inquiryWriteLike -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Inquiry/question turn: answer without executing write tools.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = IntentResolvedAction.ADVISE,
            confidenceBand = confidenceBand,
            riskClass = riskClass,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )

        creativeSelectorNeeded -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = true,
            rationale = "High-impact creative branching requires selector choice before write execution.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = IntentResolvedAction.WRITE_CREATE,
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.SAFE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )

        intentClass == ChatIntentClass.DESTRUCTIVE || destructiveByResolution -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = true,
            requireSelectorForCreative = false,
            rationale = "Destructive action requires selector confirmation before execution.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = IntentResolvedAction.WRITE_DELETE,
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.DESTRUCTIVE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = true,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )

        writeAllowedBySignal || (writeAllowedByResolution && resolutionConfident && executeWriteReady) -> ChatTurnPolicy(
            intentClass = if (intentClass == ChatIntentClass.AMBIGUOUS) ChatIntentClass.WRITE else intentClass,
            decisionPath = ChatDecisionPath.DIRECT_WRITE,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Write enabled by classifier signal (confidence=$confidence, evidence=\"$evidence\").",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = when (resolvedAction) {
                IntentResolvedAction.WRITE_DELETE -> IntentResolvedAction.WRITE_UPDATE
                IntentResolvedAction.FOLLOW_UP,
                IntentResolvedAction.ADVISE,
                -> IntentResolvedAction.WRITE_UPDATE
                else -> resolvedAction
            },
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.SAFE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )

        intentClass == ChatIntentClass.CREATIVE || resolvedAction == IntentResolvedAction.ADVISE -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Creative/advisory request. Respond without write tools.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = IntentResolvedAction.ADVISE,
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.SAFE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )

        else -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.FOLLOW_UP,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Intent or target is ambiguous/low-confidence. Ask one focused follow-up.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidenceBand = intentSignal.preferenceConfidenceBand,
            preferenceNovelty = intentSignal.preferenceNovelty,
            resolvedAction = IntentResolvedAction.FOLLOW_UP,
            confidenceBand = confidenceBand,
            riskClass = riskClass,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )
    }
}

fun buildTurnPolicy(request: ChatRequest, intentClass: ChatIntentClass): ChatTurnPolicy =
    buildTurnPolicy(
        request = request,
        intentSignal = ChatIntentSignal(
            intentClass = intentClass,
            explicitWriteIntent = intentClass == ChatIntentClass.WRITE || intentClass == ChatIntentClass.DESTRUCTIVE,
            confidence = 1.0,
            evidenceSpan = "",
            reasoning = "Legacy intent-only policy path.",
        ),
    )

private fun String.normalizedStableHash(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .hashCode()
        .toString()
