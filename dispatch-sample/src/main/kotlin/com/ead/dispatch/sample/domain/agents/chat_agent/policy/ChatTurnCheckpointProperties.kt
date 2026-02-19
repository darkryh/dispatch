package com.ead.dispatch.sample.domain.agents.chat_agent.policy

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object ChatTurnCheckpointProperties {
    const val INTENT_CLASS = "dispatch.chat.intent_class"
    const val DECISION_PATH = "dispatch.chat.decision_path"
    const val EXPLICIT_WRITE_INTENT = "dispatch.chat.explicit_write_intent"
    const val ALLOW_WRITE_TOOLS = "dispatch.chat.allow_write_tools"
    const val REQUIRE_SELECTOR_FOR_DESTRUCTIVE = "dispatch.chat.require_selector_for_destructive"
    const val POLICY_RATIONALE = "dispatch.chat.policy_rationale"
    const val RESOLVED_ACTION = "dispatch.chat.resolved_action"
    const val CONFIDENCE_BAND = "dispatch.chat.confidence_band"
    const val RISK_CLASS = "dispatch.chat.risk_class"
    const val ANCHOR_HINT = "dispatch.chat.anchor_hint"
    const val REQUIRES_CONFIRMATION = "dispatch.chat.requires_confirmation"
    const val SHOULD_SAVE_PREFERENCE = "dispatch.chat.should_save_preference"
    const val PREFERENCE_CONCEPTS = "dispatch.chat.preference_concepts"
    const val PREFERENCE_CONFIDENCE = "dispatch.chat.preference_confidence"
    const val PREFERENCE_EVIDENCE = "dispatch.chat.preference_evidence"

    const val PREFERENCE_SAVE_RECOMMENDED = "dispatch.chat.preference_save_recommended"
    const val PREFERENCE_CONCEPTS_SUGGESTED = "dispatch.chat.preference_concepts_suggested"
    const val PREFERENCE_SAVE_EXECUTED = "dispatch.chat.preference_save_executed"
    const val PREFERENCE_SAVE_SKIPPED_REASON = "dispatch.chat.preference_save_skipped_reason"
    const val REQUESTED_TOOL_CALLS = "dispatch.chat.requested_tool_calls"
    const val EXECUTED_TOOL_CALLS = "dispatch.chat.executed_tool_calls"
    const val BLOCKED_TOOL_CALLS = "dispatch.chat.blocked_tool_calls"
    const val FAILED_TOOL_CALLS = "dispatch.chat.failed_tool_calls"
    const val WRITE_TOOL_CALLS = "dispatch.chat.write_tool_calls"
    const val WRITE_TOOL_CALLS_BLOCKED = "dispatch.chat.write_tool_calls_blocked"
    const val DECISION_TOOL_CALLS = "dispatch.chat.decision_tool_calls"
    const val MUTATION_CREATE_COUNT = "dispatch.chat.mutation_create_count"
    const val MUTATION_UPDATE_COUNT = "dispatch.chat.mutation_update_count"
    const val MUTATION_DELETE_COUNT = "dispatch.chat.mutation_delete_count"
    const val SELECTOR_SHOWN = "dispatch.chat.selector_shown"
    const val AUDIT_NODE_VISITED = "dispatch.chat.audit_node_visited"

    fun merge(
        existing: Map<String, JsonElement>,
        policy: ChatTurnPolicy?,
        metrics: ChatTurnMetrics?,
    ): Map<String, JsonElement> {
        val merged = existing.toMutableMap()

        if (policy != null) {
            merged[INTENT_CLASS] = JsonPrimitive(policy.intentClass.name)
            merged[DECISION_PATH] = JsonPrimitive(policy.decisionPath.name)
            merged[EXPLICIT_WRITE_INTENT] = JsonPrimitive(policy.explicitWriteIntent)
            merged[ALLOW_WRITE_TOOLS] = JsonPrimitive(policy.allowWriteTools)
            merged[REQUIRE_SELECTOR_FOR_DESTRUCTIVE] = JsonPrimitive(policy.requireSelectorForDestructive)
            merged[POLICY_RATIONALE] = JsonPrimitive(policy.rationale)
            merged[RESOLVED_ACTION] = JsonPrimitive(policy.resolvedAction.name)
            merged[CONFIDENCE_BAND] = JsonPrimitive(policy.confidenceBand.name)
            merged[RISK_CLASS] = JsonPrimitive(policy.riskClass.name)
            merged[ANCHOR_HINT] = JsonPrimitive(policy.anchorHint)
            merged[REQUIRES_CONFIRMATION] = JsonPrimitive(policy.requiresConfirmation)
            merged[SHOULD_SAVE_PREFERENCE] = JsonPrimitive(policy.shouldSavePreference)
            merged[PREFERENCE_CONCEPTS] = JsonPrimitive(policy.preferenceConceptKeywords.joinToString(","))
            merged[PREFERENCE_CONFIDENCE] = JsonPrimitive(policy.preferenceConfidence)
            merged[PREFERENCE_EVIDENCE] = JsonPrimitive(policy.preferenceEvidenceSpan)
        }

        if (metrics != null) {
            merged[PREFERENCE_SAVE_RECOMMENDED] = JsonPrimitive(metrics.preferenceSaveRecommended)
            merged[PREFERENCE_CONCEPTS_SUGGESTED] = JsonPrimitive(metrics.preferenceConceptsSuggested)
            merged[PREFERENCE_SAVE_EXECUTED] = JsonPrimitive(metrics.preferenceSaveExecuted)
            merged[PREFERENCE_SAVE_SKIPPED_REASON] = JsonPrimitive(metrics.preferenceSaveSkippedReason)
            merged[REQUESTED_TOOL_CALLS] = JsonPrimitive(metrics.requestedToolCalls)
            merged[EXECUTED_TOOL_CALLS] = JsonPrimitive(metrics.executedToolCalls)
            merged[BLOCKED_TOOL_CALLS] = JsonPrimitive(metrics.blockedToolCalls)
            merged[FAILED_TOOL_CALLS] = JsonPrimitive(metrics.failedToolCalls)
            merged[WRITE_TOOL_CALLS] = JsonPrimitive(metrics.writeToolCalls)
            merged[WRITE_TOOL_CALLS_BLOCKED] = JsonPrimitive(metrics.writeToolCallsBlocked)
            merged[DECISION_TOOL_CALLS] = JsonPrimitive(metrics.decisionToolCalls)
            merged[MUTATION_CREATE_COUNT] = JsonPrimitive(metrics.mutationCreateCount)
            merged[MUTATION_UPDATE_COUNT] = JsonPrimitive(metrics.mutationUpdateCount)
            merged[MUTATION_DELETE_COUNT] = JsonPrimitive(metrics.mutationDeleteCount)
            merged[SELECTOR_SHOWN] = JsonPrimitive(metrics.selectorShown)
            merged[AUDIT_NODE_VISITED] = JsonPrimitive(metrics.auditNodeVisited)
        }

        return merged
    }
}
