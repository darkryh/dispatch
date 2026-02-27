package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.policy.ContextRiskZone

enum class CompressionTiming {
    BEFORE_NEXT_LLM,
    END_OF_TURN,
    MANUAL,
}

enum class ContextLifecycleStage {
    BEFORE_LLM,
    AFTER_LLM,
    BEFORE_TOOL_LOOP,
    AFTER_TOOL_LOOP,
    END_TURN,
}

data class CompressionPlan(
    val mode: CompressionMode,
    val timing: CompressionTiming,
    val reason: String,
    val riskZone: ContextRiskZone,
    val requiresLlmRoundtrip: Boolean,
) {
    val shouldCompress: Boolean get() = mode != CompressionMode.NONE
}

