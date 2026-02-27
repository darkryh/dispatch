package com.ead.koog.context.orchestrator.state

import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry

data class ContextSnapshot(
    val telemetry: ContextTelemetry,
    val continuityPacket: ContinuityPacket?,
    val latestAppliedArtifactId: String? = null,
    val latestAppliedArtifactVersion: Long? = null,
)
