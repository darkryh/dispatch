package com.ead.dispatch.sample.domain.agents.story_agent.policy

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object StoryTurnCheckpointProperties {
    const val INTENT_CLASS = "dispatch.story.intent_class"
    const val DECISION_PATH = "dispatch.story.decision_path"
    const val EXPLICIT_WRITE_INTENT = "dispatch.story.explicit_write_intent"
    const val ALLOW_WRITE_TOOLS = "dispatch.story.allow_write_tools"
    const val REQUIRE_SELECTOR_FOR_DESTRUCTIVE = "dispatch.story.require_selector_for_destructive"
    const val REQUIRE_SELECTOR_FOR_CREATIVE = "dispatch.story.require_selector_for_creative"
    const val POLICY_RATIONALE = "dispatch.story.policy_rationale"
    const val RESOLVED_ACTION = "dispatch.story.resolved_action"
    const val CONFIDENCE_BAND = "dispatch.story.confidence_band"
    const val RISK_CLASS = "dispatch.story.risk_class"
    const val ANCHOR_HINT = "dispatch.story.anchor_hint"
    const val REQUIRES_CONFIRMATION = "dispatch.story.requires_confirmation"

    const val REQUESTED_TOOL_CALLS = "dispatch.story.requested_tool_calls"
    const val EXECUTED_TOOL_CALLS = "dispatch.story.executed_tool_calls"
    const val BLOCKED_TOOL_CALLS = "dispatch.story.blocked_tool_calls"
    const val FAILED_TOOL_CALLS = "dispatch.story.failed_tool_calls"
    const val WRITE_TOOL_CALLS = "dispatch.story.write_tool_calls"
    const val WRITE_TOOL_CALLS_BLOCKED = "dispatch.story.write_tool_calls_blocked"
    const val DECISION_TOOL_CALLS = "dispatch.story.decision_tool_calls"
    const val MUTATION_CREATE_COUNT = "dispatch.story.mutation_create_count"
    const val MUTATION_UPDATE_COUNT = "dispatch.story.mutation_update_count"
    const val MUTATION_DELETE_COUNT = "dispatch.story.mutation_delete_count"
    const val SELECTOR_SHOWN = "dispatch.story.selector_shown"
    const val AUDIT_NODE_VISITED = "dispatch.story.audit_node_visited"

    fun merge(
        existing: Map<String, JsonElement>,
        policy: StoryTurnPolicy?,
        metrics: StoryTurnMetrics?,
    ): Map<String, JsonElement> {
        val merged = existing.toMutableMap()

        if (policy != null) {
            merged[INTENT_CLASS] = JsonPrimitive(policy.intentClass.name)
            merged[DECISION_PATH] = JsonPrimitive(policy.decisionPath.name)
            merged[EXPLICIT_WRITE_INTENT] = JsonPrimitive(policy.explicitWriteIntent)
            merged[ALLOW_WRITE_TOOLS] = JsonPrimitive(policy.allowWriteTools)
            merged[REQUIRE_SELECTOR_FOR_DESTRUCTIVE] = JsonPrimitive(policy.requireSelectorForDestructive)
            merged[REQUIRE_SELECTOR_FOR_CREATIVE] = JsonPrimitive(policy.requireSelectorForCreative)
            merged[POLICY_RATIONALE] = JsonPrimitive(policy.rationale)
            merged[RESOLVED_ACTION] = JsonPrimitive(policy.resolvedAction.name)
            merged[CONFIDENCE_BAND] = JsonPrimitive(policy.confidenceBand.name)
            merged[RISK_CLASS] = JsonPrimitive(policy.riskClass.name)
            merged[ANCHOR_HINT] = JsonPrimitive(policy.anchorHint)
            merged[REQUIRES_CONFIRMATION] = JsonPrimitive(policy.requiresConfirmation)
        }

        if (metrics != null) {
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
