package com.ead.dispatch.sample.domain.agents.chat_agent.policy

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
            rationale = "User answered a selector prompt; continue execution with writes enabled.",
            fromDecisionPrompt = true,
            requestTextHash = requestTextHash,
        )
    }

    val intentClass = intentSignal.intentClass
    val confidence = intentSignal.confidence.coerceIn(0.0, 1.0)
    val explicitWriteIntent = intentSignal.explicitWriteIntent
    val writeAllowedBySignal = (explicitWriteIntent && confidence >= explicitWriteConfidenceThreshold) ||
        (intentClass == ChatIntentClass.WRITE && confidence >= writeClassConfidenceThreshold)

    val evidence = intentSignal.evidenceSpan
        .trim()
        .take(120)
        .ifBlank { "none" }

    return when {
        intentClass == ChatIntentClass.DESTRUCTIVE -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = true,
            rationale = "Destructive action requires selector confirmation before execution.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidence = intentSignal.preferenceConfidence,
            preferenceEvidenceSpan = intentSignal.preferenceEvidenceSpan,
            preferenceReasoning = intentSignal.preferenceReasoning,
        )

        writeAllowedBySignal -> ChatTurnPolicy(
            intentClass = if (intentClass == ChatIntentClass.AMBIGUOUS) ChatIntentClass.WRITE else intentClass,
            decisionPath = ChatDecisionPath.DIRECT_WRITE,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            rationale = "Write enabled by classifier signal (confidence=$confidence, evidence=\"$evidence\").",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidence = intentSignal.preferenceConfidence,
            preferenceEvidenceSpan = intentSignal.preferenceEvidenceSpan,
            preferenceReasoning = intentSignal.preferenceReasoning,
        )

        intentClass == ChatIntentClass.CREATIVE -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            rationale = "Creative/advisory request. Respond without write tools.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidence = intentSignal.preferenceConfidence,
            preferenceEvidenceSpan = intentSignal.preferenceEvidenceSpan,
            preferenceReasoning = intentSignal.preferenceReasoning,
        )

        else -> ChatTurnPolicy(
            intentClass = intentClass,
            decisionPath = ChatDecisionPath.FOLLOW_UP,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            rationale = "Intent or target is ambiguous/low-confidence. Ask one focused follow-up.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
            shouldSavePreference = intentSignal.shouldSavePreference,
            preferenceConceptKeywords = intentSignal.preferenceConceptKeywords,
            preferenceConfidence = intentSignal.preferenceConfidence,
            preferenceEvidenceSpan = intentSignal.preferenceEvidenceSpan,
            preferenceReasoning = intentSignal.preferenceReasoning,
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
