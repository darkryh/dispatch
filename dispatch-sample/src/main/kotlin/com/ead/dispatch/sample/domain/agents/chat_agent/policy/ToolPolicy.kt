package com.ead.dispatch.sample.domain.agents.chat_agent.policy

private val writeToolPrefixes = listOf(
    "create",
    "update",
    "delete",
    "insert",
    "upsert",
    "replace",
    "set",
)

private val destructiveToolPrefixes = listOf(
    "delete",
    "remove",
    "clear",
    "drop",
)

private val decisionToolNames = setOf(
    "requestuserchoice",
    "requestdecision",
    "askuserchoice",
)

fun isToolAllowedForTurn(policy: ChatTurnPolicy, toolName: String?): Boolean {
    if (isDecisionToolName(toolName)) return true

    val writeTool = isWriteToolName(toolName)
    if (writeTool && !policy.allowWriteTools) return false

    // In selector-gated destructive turns, force decision-first behavior:
    // do not expose write tools until the follow-up turn confirms the decision.
    if (policy.requireSelectorForDestructive && writeTool) return false

    return true
}

fun isWriteToolName(toolName: String?): Boolean {
    val normalized = toolName?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return writeToolPrefixes.any { prefix -> normalized.startsWith(prefix) }
}

fun isDestructiveToolName(toolName: String?): Boolean {
    val normalized = toolName?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return destructiveToolPrefixes.any { prefix -> normalized.startsWith(prefix) }
}

fun isDecisionToolName(toolName: String?): Boolean {
    val normalized = toolName?.trim()?.lowercase().orEmpty()
    return normalized in decisionToolNames
}
