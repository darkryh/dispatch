package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry

data class ContextDecision(
    val mode: CompressionMode,
    val timing: CompressionTiming,
    val reason: String,
    val telemetry: ContextTelemetry,
    val stage: ContextLifecycleStage,
) {
    val shouldCompress: Boolean get() = mode != CompressionMode.NONE
}
