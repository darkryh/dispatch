package com.ead.dispatch.sample.domain.agents.story_agent.policy

import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest

private const val explicitWriteConfidenceThreshold = 0.55
private const val writeClassConfidenceThreshold = 0.40

fun buildStoryTurnPolicy(
    request: StoryRequest,
    intentSignal: StoryIntentSignal,
): StoryTurnPolicy {
    val requestTextHash = request.text.normalizedStableHash()

    if (request.fromDecisionPrompt) {
        return StoryTurnPolicy(
            intentClass = StoryIntentClass.WRITE,
            decisionPath = StoryDecisionPath.DIRECT_WRITE,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "User answered a selector prompt; continue execution with writes enabled.",
            fromDecisionPrompt = true,
            requestTextHash = requestTextHash,
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
    val shouldSavePreference = intentSignal.shouldSavePreference
    val preferenceConceptKeywords = intentSignal.preferenceConceptKeywords
    val preferenceConfidenceBand = intentSignal.preferenceConfidenceBand
    val preferenceNovelty = intentSignal.preferenceNovelty
    val writeAllowedBySignal = (explicitWriteIntent && confidence >= explicitWriteConfidenceThreshold) ||
        (intentClass == StoryIntentClass.WRITE && confidence >= writeClassConfidenceThreshold)
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
            (intentClass == StoryIntentClass.CREATIVE || intentClass == StoryIntentClass.WRITE)
    val inquiryWriteLike = executionIntent == IntentExecutionIntent.INQUIRE &&
        (intentClass == StoryIntentClass.WRITE ||
            intentClass == StoryIntentClass.DESTRUCTIVE ||
            explicitWriteIntent ||
            writeLikeByResolution)

    val evidence = intentSignal.evidenceSpan
        .trim()
        .take(120)
        .ifBlank { "none" }

    return when {
        inquiryWriteLike -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Inquiry/question turn: answer without executing write tools.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = shouldSavePreference,
            preferenceConceptKeywords = preferenceConceptKeywords,
            preferenceConfidenceBand = preferenceConfidenceBand,
            preferenceNovelty = preferenceNovelty,
            resolvedAction = IntentResolvedAction.ADVISE,
            confidenceBand = confidenceBand,
            riskClass = riskClass,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )

        creativeSelectorNeeded -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = true,
            rationale = "High-impact creative branching requires selector choice before write execution.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = shouldSavePreference,
            preferenceConceptKeywords = preferenceConceptKeywords,
            preferenceConfidenceBand = preferenceConfidenceBand,
            preferenceNovelty = preferenceNovelty,
            resolvedAction = IntentResolvedAction.WRITE_CREATE,
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.SAFE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )

        intentClass == StoryIntentClass.DESTRUCTIVE || destructiveByResolution -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = true,
            requireSelectorForCreative = false,
            rationale = "Destructive story action requires selector confirmation before execution.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = shouldSavePreference,
            preferenceConceptKeywords = preferenceConceptKeywords,
            preferenceConfidenceBand = preferenceConfidenceBand,
            preferenceNovelty = preferenceNovelty,
            resolvedAction = IntentResolvedAction.WRITE_DELETE,
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.DESTRUCTIVE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = true,
            executionIntent = IntentExecutionIntent.EXECUTE,
        )

        writeAllowedBySignal || (writeAllowedByResolution && resolutionConfident && executeWriteReady) -> StoryTurnPolicy(
            intentClass = if (intentClass == StoryIntentClass.AMBIGUOUS) StoryIntentClass.WRITE else intentClass,
            decisionPath = StoryDecisionPath.DIRECT_WRITE,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Story write enabled by classifier signal (confidence=$confidence, evidence=\"$evidence\").",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = shouldSavePreference,
            preferenceConceptKeywords = preferenceConceptKeywords,
            preferenceConfidenceBand = preferenceConfidenceBand,
            preferenceNovelty = preferenceNovelty,
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

        intentClass == StoryIntentClass.CREATIVE || resolvedAction == IntentResolvedAction.ADVISE -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Story advisory request. Respond without write tools.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = shouldSavePreference,
            preferenceConceptKeywords = preferenceConceptKeywords,
            preferenceConfidenceBand = preferenceConfidenceBand,
            preferenceNovelty = preferenceNovelty,
            resolvedAction = IntentResolvedAction.ADVISE,
            confidenceBand = confidenceBand,
            riskClass = IntentRiskClass.SAFE,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )

        else -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.FOLLOW_UP,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            requireSelectorForCreative = false,
            rationale = "Story request is ambiguous/low-confidence. Ask one focused follow-up.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = shouldSavePreference,
            preferenceConceptKeywords = preferenceConceptKeywords,
            preferenceConfidenceBand = preferenceConfidenceBand,
            preferenceNovelty = preferenceNovelty,
            resolvedAction = IntentResolvedAction.FOLLOW_UP,
            confidenceBand = confidenceBand,
            riskClass = riskClass,
            anchorHint = intentSignal.anchorHint,
            requiresConfirmation = false,
            executionIntent = IntentExecutionIntent.INQUIRE,
        )
    }
}

private fun String.normalizedStableHash(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .hashCode()
        .toString()
