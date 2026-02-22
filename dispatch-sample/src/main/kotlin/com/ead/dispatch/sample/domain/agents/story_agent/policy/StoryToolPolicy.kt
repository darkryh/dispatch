package com.ead.dispatch.sample.domain.agents.story_agent.policy

private val writeToolPrefixes = listOf(
    "create",
    "update",
    "delete",
    "insert",
    "upsert",
    "replace",
    "set",
    "apply",
    "rollback",
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

fun isStoryToolAllowedForTurn(policy: StoryTurnPolicy, toolName: String?): Boolean {
    if (isStoryDecisionToolName(toolName)) return true

    // Selector turns are terminal choice turns: only decision tools are allowed.
    if (policy.requireSelectorForDestructive || policy.requireSelectorForCreative) return false

    val writeTool = isStoryWriteToolName(toolName)
    if (writeTool && !policy.allowWriteTools) return false

    return true
}

fun isStoryWriteToolName(toolName: String?): Boolean {
    val normalized = toolName?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return writeToolPrefixes.any { prefix -> normalized.startsWith(prefix) }
}

fun isStoryDestructiveToolName(toolName: String?): Boolean {
    val normalized = toolName?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    return destructiveToolPrefixes.any { prefix -> normalized.startsWith(prefix) }
}

fun isStoryDecisionToolName(toolName: String?): Boolean {
    val normalized = toolName?.trim()?.lowercase().orEmpty()
    return normalized in decisionToolNames
}
