package com.ead.koog.context.orchestrator.policy

import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.CompressionPlan
import com.ead.koog.context.orchestrator.api.CompressionTiming
import com.ead.koog.context.orchestrator.api.ContextManagementConfig
import com.ead.koog.context.orchestrator.telemetry.ContextTelemetry
import kotlin.math.roundToInt

class AdaptiveContextBudgetManager(
    private val config: ContextManagementConfig,
) {
    private val observedPromptTokenTotals = ArrayDeque<Int>()

    fun observe(totalPromptTokens: Int) {
        observedPromptTokenTotals.addLast(totalPromptTokens)
        if (observedPromptTokenTotals.size > 8) {
            observedPromptTokenTotals.removeFirst()
        }
    }

    fun growthTokensPerTurn(): Int = 0

    fun riskZone(usedPercent: Double): ContextRiskZone = when {
        usedPercent >= config.emergencyAtUsedPercent -> ContextRiskZone.EMERGENCY
        usedPercent >= config.criticalAtUsedPercent -> ContextRiskZone.CRITICAL
        usedPercent >= config.warningAtUsedPercent -> ContextRiskZone.WARNING
        usedPercent >= config.watchAtUsedPercent -> ContextRiskZone.WATCH
        else -> ContextRiskZone.HEALTHY
    }

    fun decidePlan(
        messageCount: Int,
        turnsSinceLastCompression: Int?,
        zone: ContextRiskZone,
        hints: ContextHints,
    ): CompressionPlan {
        if (messageCount < config.minMessagesForCompression) {
            return CompressionPlan(
                mode = CompressionMode.NONE,
                timing = CompressionTiming.MANUAL,
                reason = "Message count is below minimum compression threshold.",
                riskZone = zone,
                requiresLlmRoundtrip = false,
            )
        }

        val withinCooldown =
            turnsSinceLastCompression != null &&
                turnsSinceLastCompression <= config.compressionCooldownTurns

        if (withinCooldown && zone <= ContextRiskZone.WARNING) {
            return CompressionPlan(
                mode = CompressionMode.NONE,
                timing = CompressionTiming.MANUAL,
                reason = "Compression cooldown is active.",
                riskZone = zone,
                requiresLlmRoundtrip = false,
            )
        }

        val configuredPolicy = when (zone) {
            ContextRiskZone.HEALTHY -> null
            ContextRiskZone.WATCH -> config.watchPolicy
            ContextRiskZone.WARNING -> config.warningPolicy
            ContextRiskZone.CRITICAL -> config.criticalPolicy
            ContextRiskZone.EMERGENCY -> config.emergencyPolicy
        }
        if (configuredPolicy == null) {
            return CompressionPlan(
                mode = CompressionMode.NONE,
                timing = CompressionTiming.MANUAL,
                reason = "Context is healthy. No compression policy is applied.",
                riskZone = zone,
                requiresLlmRoundtrip = false,
            )
        }

        if (hints.recentToolCalls < configuredPolicy.minRecentToolCalls) {
            return CompressionPlan(
                mode = CompressionMode.NONE,
                timing = CompressionTiming.MANUAL,
                reason = "Recent tool calls are below policy minimum for this zone.",
                riskZone = zone,
                requiresLlmRoundtrip = false,
            )
        }

        val mode = resolveModeWithHints(zone, configuredPolicy.mode, hints)

        val timing = if (mode == CompressionMode.NONE) CompressionTiming.MANUAL else configuredPolicy.timing
        val reason = when (mode) {
            CompressionMode.NONE -> "Context is within configured budget."
            CompressionMode.LIGHT -> "Light compression requested by zone policy."
            CompressionMode.STRUCTURED -> "Structured compression requested by zone policy."
            CompressionMode.AGGRESSIVE -> "Aggressive compression requested by zone policy."
            CompressionMode.FACT_FOCUSED -> "Fact-focused compression requested by zone policy."
            CompressionMode.EMERGENCY -> "Emergency compression requested by zone policy."
        }

        return CompressionPlan(
            mode = mode,
            timing = timing,
            reason = reason,
            riskZone = zone,
            requiresLlmRoundtrip = mode != CompressionMode.NONE,
        )
    }

    private fun resolveModeWithHints(
        zone: ContextRiskZone,
        requested: CompressionMode,
        hints: ContextHints,
    ): CompressionMode {
        if (requested != CompressionMode.FACT_FOCUSED) return requested
        if (hints.factConcepts.isEmpty()) {
            return if (zone >= ContextRiskZone.CRITICAL) CompressionMode.AGGRESSIVE else CompressionMode.STRUCTURED
        }
        if (zone == ContextRiskZone.WARNING && hints.unresolvedCommitments <= 0) {
            return CompressionMode.STRUCTURED
        }
        return CompressionMode.FACT_FOCUSED
    }

    fun telemetry(
        estimatedPromptTokens: Int,
        tokenUsageKnown: Boolean,
        compactionCount: Int,
        turnsSinceLastCompaction: Int?,
        lastCompactionMode: CompressionMode?,
        continuityIntegrityScore: Int,
    ): ContextTelemetry {
        val safeEstimated = estimatedPromptTokens.coerceAtLeast(0)
        val usedPercent = ((safeEstimated.toDouble() / config.maxContextTokens) * 100.0)
            .coerceIn(0.0, 100.0)
        val remainingTokens = (config.maxContextTokens - safeEstimated).coerceAtLeast(0)
        val remainingPercent = (100.0 - usedPercent).coerceIn(0.0, 100.0)

        return ContextTelemetry(
            estimatedPromptTokens = safeEstimated,
            tokenUsageKnown = tokenUsageKnown,
            maxContextTokens = config.maxContextTokens,
            usedPercent = (usedPercent * 100.0).roundToInt() / 100.0,
            remainingTokens = remainingTokens,
            remainingPercent = (remainingPercent * 100.0).roundToInt() / 100.0,
            riskZone = riskZone(usedPercent),
            compactionCount = compactionCount,
            turnsSinceLastCompaction = turnsSinceLastCompaction,
            lastCompactionMode = lastCompactionMode,
            growthTokensPerTurn = 0,
            continuityIntegrityScore = continuityIntegrityScore.coerceIn(0, 100),
        )
    }
}
