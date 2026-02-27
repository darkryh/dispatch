package com.ead.koog.context.orchestrator.telemetry

import com.ead.koog.context.orchestrator.policy.CompressionMode
import com.ead.koog.context.orchestrator.policy.ContextRiskZone

data class ContextTelemetry(
    val estimatedPromptTokens: Int,
    val tokenUsageKnown: Boolean,
    val maxContextTokens: Int,
    val usedPercent: Double,
    val remainingTokens: Int,
    val remainingPercent: Double,
    val riskZone: ContextRiskZone,
    val compactionCount: Int,
    val turnsSinceLastCompaction: Int?,
    val lastCompactionMode: CompressionMode?,
    val growthTokensPerTurn: Int,
    val continuityIntegrityScore: Int,
)
