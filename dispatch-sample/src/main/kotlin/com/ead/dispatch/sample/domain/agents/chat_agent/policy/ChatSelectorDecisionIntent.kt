package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import com.ead.dispatch.sample.domain.agents.chat_agent.ChatDecisionContext
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.intent.IntentConfidenceBand
import com.ead.dispatch.sample.domain.agents.intent.IntentExecutionIntent
import com.ead.dispatch.sample.domain.agents.intent.IntentResolvedAction
import com.ead.dispatch.sample.domain.agents.intent.IntentRiskClass

/**
 * Selector-origin turns are treated as execution continuations.
 * This keeps selector behavior explicit and separate from base intent parsing.
 */
internal fun applySelectorDecisionIntentSubtype(
    request: ChatRequest,
    signal: ChatIntentSignal,
): ChatIntentSignal {
    if (!request.fromDecisionPrompt) return signal

    return signal.copy(
        intentClass = ChatIntentClass.WRITE,
        explicitWriteIntent = true,
        confidence = 1.0,
        resolvedAction = IntentResolvedAction.WRITE_UPDATE,
        confidenceBand = IntentConfidenceBand.HIGH,
        riskClass = IntentRiskClass.SAFE,
        requiresConfirmation = false,
        requiresCreativeChoice = false,
        decisionBeforePersist = false,
        executionIntent = IntentExecutionIntent.EXECUTE,
    )
}

internal fun selectorDecisionMetadata(decisionContext: ChatDecisionContext?): List<String> {
    if (decisionContext == null) return listOf("decision_context: (none)")

    return listOf(
        "decision_prompt_id: ${decisionContext.promptId}",
        "decision_question: ${decisionContext.question}",
        "decision_options: ${decisionContext.optionLabels.joinToString(" | ")}",
        "decision_selected_value: ${decisionContext.selectedValue}",
        "decision_is_custom_selection: ${decisionContext.isCustomSelection}",
    )
}
