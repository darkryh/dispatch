package com.ead.dispatch.sample.domain.agents.story_agent.policy

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
            rationale = "User answered a selector prompt; continue execution with writes enabled.",
            fromDecisionPrompt = true,
            requestTextHash = requestTextHash,
        )
    }

    val intentClass = intentSignal.intentClass
    val confidence = intentSignal.confidence.coerceIn(0.0, 1.0)
    val explicitWriteIntent = intentSignal.explicitWriteIntent
    val writeAllowedBySignal = (explicitWriteIntent && confidence >= explicitWriteConfidenceThreshold) ||
        (intentClass == StoryIntentClass.WRITE && confidence >= writeClassConfidenceThreshold)

    val evidence = intentSignal.evidenceSpan
        .trim()
        .take(120)
        .ifBlank { "none" }

    return when {
        intentClass == StoryIntentClass.DESTRUCTIVE -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.SELECTOR,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = true,
            rationale = "Destructive story action requires selector confirmation before execution.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
        )

        writeAllowedBySignal -> StoryTurnPolicy(
            intentClass = if (intentClass == StoryIntentClass.AMBIGUOUS) StoryIntentClass.WRITE else intentClass,
            decisionPath = StoryDecisionPath.DIRECT_WRITE,
            explicitWriteIntent = true,
            allowWriteTools = true,
            requireSelectorForDestructive = false,
            rationale = "Story write enabled by classifier signal (confidence=$confidence, evidence=\"$evidence\").",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
        )

        intentClass == StoryIntentClass.CREATIVE -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.DIRECT_RESPONSE,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            rationale = "Story advisory request. Respond without write tools.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
        )

        else -> StoryTurnPolicy(
            intentClass = intentClass,
            decisionPath = StoryDecisionPath.FOLLOW_UP,
            explicitWriteIntent = false,
            allowWriteTools = false,
            requireSelectorForDestructive = false,
            rationale = "Story request is ambiguous/low-confidence. Ask one focused follow-up.",
            fromDecisionPrompt = false,
            requestTextHash = requestTextHash,
        )
    }
}

private fun String.normalizedStableHash(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .hashCode()
        .toString()
