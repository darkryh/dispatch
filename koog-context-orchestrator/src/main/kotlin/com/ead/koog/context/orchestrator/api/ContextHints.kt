package com.ead.koog.context.orchestrator.api

import ai.koog.agents.memory.model.Concept
import com.ead.koog.context.orchestrator.state.ContinuityPacket

/**
 * Optional hints that steer policy decisions for a given execution step.
 */
data class ContextHints(
    val phase: TaskPhase = TaskPhase.EXECUTION,
    val unresolvedCommitments: Int = 0,
    val recentToolCalls: Int = 0,
    val factConcepts: List<Concept> = emptyList(),
    val continuityPacket: ContinuityPacket? = null,
)

enum class TaskPhase {
    DISCOVERY,
    EXECUTION,
    REFINEMENT,
    FINALIZATION,
}
