package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.policy.ContextRiskZone
import com.ead.koog.context.orchestrator.state.ContextSnapshot
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic wrapper for agent outputs plus runtime context telemetry.
 */
data class ContextRunOutput<T>(
    val value: T,
    val telemetry: ContextRunTelemetry,
)

data class ContextRunTelemetry(
    val snapshots: StateFlow<ContextSnapshot>,
) {
    val latest: ContextSnapshot
        get() = snapshots.value

    companion object {
        fun initialSnapshot(): ContextSnapshot = ContextSnapshot(
            telemetry = ContextTelemetry(
                estimatedPromptTokens = 0,
                tokenUsageKnown = false,
                maxContextTokens = 1,
                usedPercent = 0.0,
                remainingTokens = 1,
                remainingPercent = 100.0,
                riskZone = ContextRiskZone.HEALTHY,
                compactionCount = 0,
                turnsSinceLastCompaction = null,
                lastCompactionMode = null,
                growthTokensPerTurn = 0,
                continuityIntegrityScore = 0,
            ),
            continuityPacket = null,
            latestAppliedArtifactId = null,
            latestAppliedArtifactVersion = null,
        )
    }
}
