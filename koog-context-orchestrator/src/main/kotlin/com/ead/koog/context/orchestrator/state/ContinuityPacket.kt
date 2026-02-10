package com.ead.koog.context.orchestrator.state

import com.ead.koog.context.orchestrator.api.ContextManagementConfig

/**
 * Structured mission-state that should survive history compaction.
 */
data class ContinuityPacket(
    val objective: String? = null,
    val constraints: List<String> = emptyList(),
    val acceptedDecisions: List<String> = emptyList(),
    val pendingActions: List<String> = emptyList(),
    val criticalReferences: List<String> = emptyList(),
    val latestToolOutcomes: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
) {
    fun isMeaningful(): Boolean =
        !objective.isNullOrBlank() ||
            constraints.isNotEmpty() ||
            acceptedDecisions.isNotEmpty() ||
            pendingActions.isNotEmpty() ||
            criticalReferences.isNotEmpty() ||
            latestToolOutcomes.isNotEmpty() ||
            openQuestions.isNotEmpty()

    fun integrityScore(): Int {
        var score = 0
        if (!objective.isNullOrBlank()) score += 25
        if (constraints.isNotEmpty()) score += 15
        if (acceptedDecisions.isNotEmpty()) score += 15
        if (pendingActions.isNotEmpty()) score += 20
        if (criticalReferences.isNotEmpty()) score += 15
        if (latestToolOutcomes.isNotEmpty()) score += 5
        if (openQuestions.isNotEmpty()) score += 5
        return score.coerceIn(0, 100)
    }

    fun toSystemMessage(config: ContextManagementConfig): String = buildString {
        appendLine("[CONTEXT CONTINUITY PACKET]")
        objective?.takeIf { it.isNotBlank() }?.let {
            appendLine("Objective: $it")
        }
        appendSection("Constraints", constraints, config.continuityMaxItemsPerSection)
        appendSection("Accepted decisions", acceptedDecisions, config.continuityMaxItemsPerSection)
        appendSection("Pending actions", pendingActions, config.continuityMaxItemsPerSection)
        appendSection("Critical references", criticalReferences, config.continuityMaxItemsPerSection)
        appendSection("Latest tool outcomes", latestToolOutcomes, config.continuityMaxItemsPerSection)
        appendSection("Open questions", openQuestions, config.continuityMaxItemsPerSection)
    }.trim()
}

private fun StringBuilder.appendSection(
    title: String,
    values: List<String>,
    maxItems: Int,
) {
    if (values.isEmpty()) return
    appendLine("$title:")
    values.take(maxItems).forEach { appendLine("- $it") }
}
